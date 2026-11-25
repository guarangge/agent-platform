package com.zc.agent.registry.modules.tenancy.service;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.tenancy.dto.TenancyContracts;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 管理租户层级和用户作用域授权，不承担运行时资源执行。 */
@Service
public class TenancyService {
    private final JdbcTemplate jdbcTemplate;

    public TenancyService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** 返回租户到项目的完整层级树。 */
    public List<TenancyContracts.ScopeNode> tree(SecurityUser user) {
        boolean admin = user.roleCodes().contains("ADMIN");
        List<Row> tenants = jdbcTemplate.query("SELECT id,tenant_code,tenant_name,status FROM tenants WHERE is_deleted=FALSE "
                        + (admin ? "" : "AND id=" + user.tenantId()) + " ORDER BY id",
                (rs,n) -> new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));
        return tenants.stream().map(t -> node(t, organizations(t.id()))).toList();
    }

    @Transactional
    public Long createTenant(TenancyContracts.SaveTenantRequest request, SecurityUser user) {
        requireAdmin(user);
        return jdbcTemplate.queryForObject("INSERT INTO tenants(tenant_code,tenant_name,created_by,updated_by) VALUES(?,?,?,?) RETURNING id",
                Long.class, normalize(request.code()), request.name().trim(), user.userId(), user.userId());
    }

    @Transactional
    public Long createOrganization(TenancyContracts.SaveOrganizationRequest request, SecurityUser user) {
        requireTenantAdmin(user, request.tenantId());
        return jdbcTemplate.queryForObject("INSERT INTO organizations(tenant_id,organization_code,organization_name) VALUES(?,?,?) RETURNING id",
                Long.class, request.tenantId(), normalize(request.code()), request.name().trim());
    }

    @Transactional
    public Long createWorkspace(TenancyContracts.SaveWorkspaceRequest request, SecurityUser user) {
        requireTenantAdmin(user, request.tenantId());
        assertCount("SELECT count(1) FROM organizations WHERE id=? AND tenant_id=? AND is_deleted=FALSE",
                "组织不属于目标租户", request.organizationId(), request.tenantId());
        return jdbcTemplate.queryForObject("INSERT INTO workspaces(tenant_id,organization_id,workspace_code,workspace_name) VALUES(?,?,?,?) RETURNING id",
                Long.class, request.tenantId(), request.organizationId(), normalize(request.code()), request.name().trim());
    }

    @Transactional
    public Long createProject(TenancyContracts.SaveProjectRequest request, SecurityUser user) {
        requireTenantAdmin(user, request.tenantId());
        assertCount("SELECT count(1) FROM workspaces WHERE id=? AND tenant_id=? AND organization_id=? AND is_deleted=FALSE",
                "工作区不属于目标租户和组织", request.workspaceId(), request.tenantId(), request.organizationId());
        return jdbcTemplate.queryForObject("INSERT INTO projects(tenant_id,organization_id,workspace_id,project_code,project_name) VALUES(?,?,?,?,?) RETURNING id",
                Long.class, request.tenantId(), request.organizationId(), request.workspaceId(), normalize(request.code()), request.name().trim());
    }

    @Transactional
    public Long grant(TenancyContracts.SaveMembershipRequest request, SecurityUser user) {
        requireTenantAdmin(user, request.tenantId());
        validateHierarchy(request.tenantId(), request.organizationId(), request.workspaceId(), request.projectId());
        if (Boolean.TRUE.equals(request.defaultScope())) {
            jdbcTemplate.update("UPDATE user_scope_memberships SET is_default=FALSE,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND is_deleted=FALSE", request.userId());
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO user_scope_memberships(user_id,tenant_id,organization_id,workspace_id,project_id,membership_role,is_default)
                VALUES(?,?,?,?,?,?,?) RETURNING id
                """, Long.class, request.userId(), request.tenantId(), request.organizationId(), request.workspaceId(),
                request.projectId(), request.membershipRole().trim(), Boolean.TRUE.equals(request.defaultScope()));
    }

    public List<TenancyContracts.MembershipItem> memberships(Long userId, SecurityUser user) {
        if (!user.roleCodes().contains("ADMIN") && !user.userId().equals(userId)) throw new BusinessException("无权查询成员关系");
        return jdbcTemplate.query("""
                SELECT m.id,m.user_id,u.username,m.tenant_id,m.organization_id,m.workspace_id,m.project_id,
                       m.membership_role,m.is_default,m.status FROM user_scope_memberships m
                JOIN users u ON u.id=m.user_id WHERE m.user_id=? AND m.is_deleted=FALSE ORDER BY m.is_default DESC,m.id
                """, (rs,n) -> new TenancyContracts.MembershipItem(rs.getLong(1),rs.getLong(2),rs.getString(3),
                rs.getLong(4),nullable(rs,5),nullable(rs,6),nullable(rs,7),rs.getString(8),rs.getBoolean(9),rs.getString(10)), userId);
    }

    private List<TenancyContracts.ScopeNode> organizations(Long tenantId) {
        return rows("SELECT id,organization_code,organization_name,status FROM organizations WHERE tenant_id=? AND is_deleted=FALSE ORDER BY id", tenantId)
                .stream().map(o -> node(o, workspaces(tenantId,o.id()))).toList();
    }
    private List<TenancyContracts.ScopeNode> workspaces(Long tenantId, Long orgId) {
        return rows("SELECT id,workspace_code,workspace_name,status FROM workspaces WHERE tenant_id=? AND organization_id=? AND is_deleted=FALSE ORDER BY id",tenantId,orgId)
                .stream().map(w -> node(w, projects(tenantId,w.id()))).toList();
    }
    private List<TenancyContracts.ScopeNode> projects(Long tenantId, Long workspaceId) {
        return rows("SELECT id,project_code,project_name,status FROM projects WHERE tenant_id=? AND workspace_id=? AND is_deleted=FALSE ORDER BY id",tenantId,workspaceId)
                .stream().map(p -> node(p,List.of())).toList();
    }
    private List<Row> rows(String sql,Object... params) { return jdbcTemplate.query(sql,(rs,n)->new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),params); }
    private TenancyContracts.ScopeNode node(Row row,List<TenancyContracts.ScopeNode> children) { return new TenancyContracts.ScopeNode(row.id(),row.code(),row.name(),row.status(),children); }
    private void validateHierarchy(Long tenantId,Long orgId,Long workspaceId,Long projectId) {
        if (orgId != null) assertCount("SELECT count(1) FROM organizations WHERE id=? AND tenant_id=? AND is_deleted=FALSE","组织作用域不合法",orgId,tenantId);
        if (workspaceId != null) assertCount("SELECT count(1) FROM workspaces WHERE id=? AND tenant_id=? AND (? IS NULL OR organization_id=?) AND is_deleted=FALSE","工作区作用域不合法",workspaceId,tenantId,orgId,orgId);
        if (projectId != null) assertCount("SELECT count(1) FROM projects WHERE id=? AND tenant_id=? AND (? IS NULL OR workspace_id=?) AND is_deleted=FALSE","项目作用域不合法",projectId,tenantId,workspaceId,workspaceId);
    }
    private void assertCount(String sql,String message,Object... args) { Long count=jdbcTemplate.queryForObject(sql,Long.class,args); if(count==null||count==0) throw new BusinessException(message); }
    private void requireAdmin(SecurityUser user) { if(!user.roleCodes().contains("ADMIN")) throw new BusinessException("仅平台管理员可创建租户"); }
    private void requireTenantAdmin(SecurityUser user,Long tenantId) { if(!user.roleCodes().contains("ADMIN") && !user.tenantId().equals(tenantId)) throw new BusinessException("无权管理目标租户"); }
    private String normalize(String code) { return code.trim().toUpperCase(); }
    private static Long nullable(java.sql.ResultSet rs,int index) throws java.sql.SQLException { long v=rs.getLong(index); return rs.wasNull()?null:v; }
    private record Row(Long id,String code,String name,String status) { }
}
