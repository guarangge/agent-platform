package com.zc.agent.registry.modules.auth.repository;

import com.zc.agent.registry.modules.auth.model.AuthRoleRecord;
import com.zc.agent.registry.modules.auth.model.AuthUserRecord;
import com.zc.agent.registry.modules.auth.model.SecurityScopeRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 认证查询仓储。
 */
@Repository
public class AuthRepository {

    private static final RowMapper<AuthUserRecord> USER_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AuthUserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuthUserRecord(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("display_name"),
                    rs.getString("status")
            );
        }
    };

    private static final RowMapper<AuthRoleRecord> ROLE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AuthRoleRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AuthRoleRecord(
                    rs.getLong("role_id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("menu_scope")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return 用户信息
     */
    public Optional<AuthUserRecord> findEnabledUserByUsername(String username) {
        String sql = """
                SELECT id, username, password_hash, display_name, status
                FROM users
                WHERE username = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<AuthUserRecord> records = jdbcTemplate.query(sql, USER_ROW_MAPPER, username);
        return records.stream().findFirst();
    }

    /**
     * 按用户 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public Optional<AuthUserRecord> findEnabledUserById(Long userId) {
        String sql = """
                SELECT id, username, password_hash, display_name, status
                FROM users
                WHERE id = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<AuthUserRecord> records = jdbcTemplate.query(sql, USER_ROW_MAPPER, userId);
        return records.stream().findFirst();
    }

    /**
     * 查询用户角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    public List<AuthRoleRecord> findEnabledRolesByUserId(Long userId) {
        String sql = """
                SELECT r.id AS role_id,
                       r.role_code,
                       r.role_name,
                       COALESCE(r.menu_scope::text, '[]') AS menu_scope
                FROM roles r
                INNER JOIN user_role_rel ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                  AND ur.status = 'enabled'
                  AND ur.is_deleted = FALSE
                  AND r.status = 'enabled'
                  AND r.is_deleted = FALSE
                ORDER BY r.id ASC
                """;
        return jdbcTemplate.query(sql, ROLE_ROW_MAPPER, userId);
    }

    /** 查询用户全部有效租户作用域，默认作用域优先。 */
    public List<SecurityScopeRecord> findEnabledScopesByUserId(Long userId) {
        return jdbcTemplate.query("""
                SELECT m.id, t.id tenant_id, t.tenant_code, t.tenant_name,
                       o.id organization_id, o.organization_name,
                       w.id workspace_id, w.workspace_name,
                       p.id project_id, p.project_name,
                       m.membership_role, m.is_default
                FROM user_scope_memberships m
                JOIN tenants t ON t.id=m.tenant_id AND t.status='enabled' AND t.is_deleted=FALSE
                LEFT JOIN organizations o ON o.id=m.organization_id AND o.status='enabled' AND o.is_deleted=FALSE
                LEFT JOIN workspaces w ON w.id=m.workspace_id AND w.status='enabled' AND w.is_deleted=FALSE
                LEFT JOIN projects p ON p.id=m.project_id AND p.status='enabled' AND p.is_deleted=FALSE
                WHERE m.user_id=? AND m.status='enabled' AND m.is_deleted=FALSE
                ORDER BY m.is_default DESC, m.id
                """, (rs, rowNum) -> new SecurityScopeRecord(
                rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("tenant_code"), rs.getString("tenant_name"),
                nullableLong(rs, "organization_id"), rs.getString("organization_name"),
                nullableLong(rs, "workspace_id"), rs.getString("workspace_name"),
                nullableLong(rs, "project_id"), rs.getString("project_name"),
                rs.getString("membership_role"), rs.getBoolean("is_default")
        ), userId);
    }

    public Optional<SecurityScopeRecord> findEnabledScope(Long userId, Long membershipId) {
        return findEnabledScopesByUserId(userId).stream()
                .filter(item -> item.membershipId().equals(membershipId)).findFirst();
    }

    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
    }

    /**
     * 更新最近登录时间。
     *
     * @param userId 用户 ID
     */
    public void updateLastLoginAt(Long userId) {
        String sql = """
                UPDATE users
                SET last_login_at = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        jdbcTemplate.update(sql, timestamp, timestamp, userId);
    }
}
