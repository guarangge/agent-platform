package com.zc.agent.registry.modules.role.repository;

import com.zc.agent.registry.modules.role.dto.CreateRoleRequest;
import com.zc.agent.registry.modules.role.dto.UpdateRoleRequest;
import com.zc.agent.registry.modules.role.model.RoleAgentRecord;
import com.zc.agent.registry.modules.role.model.RoleDetailRecord;
import com.zc.agent.registry.modules.role.model.RolePageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * 角色仓储。
 */
@Repository
public class RoleRepository {

    private static final RowMapper<RolePageRecord> ROLE_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public RolePageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RolePageRecord(
                    rs.getLong("id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<RoleDetailRecord> ROLE_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public RoleDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RoleDetailRecord(
                    rs.getLong("id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("status"),
                    rs.getString("menu_scope"),
                    rs.getString("description"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")),
                    getNullableLong(rs, "created_by"),
                    getNullableLong(rs, "updated_by")
            );
        }
    };

    private static final RowMapper<RoleAgentRecord> ROLE_AGENT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public RoleAgentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RoleAgentRecord(
                    rs.getLong("agent_id"),
                    rs.getString("agent_code"),
                    rs.getString("agent_name"),
                    rs.getString("status"),
                    rs.getString("version"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("bound_at"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计角色数量。
     *
     * @param roleCode 角色编码
     * @param roleName 角色名称
     * @param status   角色状态
     * @return 总数
     */
    public long countRoles(String roleCode, String roleName, String status) {
        SqlAndParams sqlAndParams = buildRoleQuerySql(
                """
                SELECT COUNT(1)
                FROM roles
                WHERE is_deleted = FALSE
                """,
                roleCode,
                roleName,
                status
        );
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页查询角色。
     *
     * @param roleCode 角色编码
     * @param roleName 角色名称
     * @param status   角色状态
     * @param offset   偏移量
     * @param limit    限制条数
     * @return 角色列表
     */
    public List<RolePageRecord> pageRoles(String roleCode, String roleName, String status, int offset, int limit) {
        SqlAndParams sqlAndParams = buildRoleQuerySql(
                """
                SELECT id, role_code, role_name, status, description, created_at
                FROM roles
                WHERE is_deleted = FALSE
                """,
                roleCode,
                roleName,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, ROLE_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按角色 ID 查询详情。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    public Optional<RoleDetailRecord> findRoleDetailById(Long roleId) {
        String sql = """
                SELECT id,
                       role_code,
                       role_name,
                       status,
                       COALESCE(menu_scope::text, '[]') AS menu_scope,
                       description,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM roles
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<RoleDetailRecord> records = jdbcTemplate.query(sql, ROLE_DETAIL_ROW_MAPPER, roleId);
        return records.stream().findFirst();
    }

    /**
     * 按角色编码查询角色 ID。
     *
     * @param roleCode 角色编码
     * @return 角色 ID
     */
    public Optional<Long> findRoleIdByRoleCode(String roleCode) {
        String sql = """
                SELECT id
                FROM roles
                WHERE role_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), roleCode);
        return records.stream().findFirst();
    }

    /**
     * 新增角色。
     *
     * @param request        新增请求
     * @param menuScopeJson  菜单范围 JSON
     * @param operatorId     操作人
     * @return 新角色 ID
     */
    public Long insertRole(CreateRoleRequest request, String menuScopeJson, Long operatorId) {
        String sql = """
                INSERT INTO roles (
                    role_code,
                    role_name,
                    status,
                    menu_scope,
                    description,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, CAST(? AS jsonb), ?, '{}'::jsonb, ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.roleCode(),
                request.roleName(),
                request.status(),
                menuScopeJson,
                request.description(),
                request.remark(),
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    /**
     * 编辑角色。
     *
     * @param roleId        角色 ID
     * @param request       编辑请求
     * @param menuScopeJson 菜单范围 JSON
     * @param operatorId    操作人
     * @return 影响行数
     */
    public int updateRole(Long roleId, UpdateRoleRequest request, String menuScopeJson, Long operatorId) {
        String sql = """
                UPDATE roles
                SET role_code = ?,
                    role_name = ?,
                    status = ?,
                    menu_scope = CAST(? AS jsonb),
                    description = ?,
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(
                sql,
                request.roleCode(),
                request.roleName(),
                request.status(),
                menuScopeJson,
                request.description(),
                request.remark(),
                timestamp,
                operatorId,
                roleId
        );
    }

    /**
     * 更新角色状态。
     *
     * @param roleId     角色 ID
     * @param status     角色状态
     * @param operatorId 操作人
     * @return 影响行数
     */
    public int updateRoleStatus(Long roleId, String status, Long operatorId) {
        String sql = """
                UPDATE roles
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, status, timestamp, operatorId, roleId);
    }

    /**
     * 查询角色已绑定 Agent。
     *
     * @param roleId 角色 ID
     * @return Agent 列表
     */
    public List<RoleAgentRecord> findRoleAgentsByRoleId(Long roleId) {
        String sql = """
                SELECT a.id AS agent_id,
                       a.agent_code,
                       a.agent_name,
                       a.status,
                       a.version,
                       a.description,
                       rar.created_at AS bound_at
                FROM role_agent_rel rar
                INNER JOIN agents a ON a.id = rar.agent_id
                WHERE rar.role_id = ?
                  AND rar.status = 'enabled'
                  AND rar.is_deleted = FALSE
                  AND a.is_deleted = FALSE
                ORDER BY a.id ASC
                """;
        return jdbcTemplate.query(sql, ROLE_AGENT_ROW_MAPPER, roleId);
    }

    /**
     * 查询角色当前已绑定 Agent ID。
     *
     * @param roleId 角色 ID
     * @return Agent ID 列表
     */
    public List<Long> findBoundAgentIdsByRoleId(Long roleId) {
        String sql = """
                SELECT agent_id
                FROM role_agent_rel
                WHERE role_id = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                ORDER BY agent_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("agent_id"), roleId);
    }

    /**
     * 查询存在的 Agent ID。
     *
     * @param agentIds Agent ID 列表
     * @return 已存在 Agent ID 列表
     */
    public List<Long> findExistingAgentIds(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id
                FROM agents
                WHERE is_deleted = FALSE
                  AND id IN (%s)
                ORDER BY id ASC
                """.formatted(buildInClausePlaceholders(agentIds));
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), agentIds.toArray());
    }

    /**
     * 逻辑删除角色 Agent 关系。
     *
     * @param roleId     角色 ID
     * @param agentIds   Agent ID 列表
     * @param operatorId 操作人
     */
    public void softDeleteRoleAgents(Long roleId, List<Long> agentIds, Long operatorId) {
        if (agentIds == null || agentIds.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE role_agent_rel
                SET is_deleted = TRUE,
                    updated_at = ?,
                    updated_by = ?
                WHERE role_id = ?
                  AND is_deleted = FALSE
                  AND agent_id IN (%s)
                """.formatted(buildInClausePlaceholders(agentIds));
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object> params = new ArrayList<>();
        params.add(timestamp);
        params.add(operatorId);
        params.add(roleId);
        params.addAll(agentIds);
        jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * 新增角色 Agent 关系。
     *
     * @param roleId     角色 ID
     * @param agentIds   Agent ID 列表
     * @param operatorId 操作人
     */
    public void insertRoleAgents(Long roleId, List<Long> agentIds, Long operatorId) {
        if (agentIds == null || agentIds.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO role_agent_rel (
                    role_id,
                    agent_id,
                    status,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, 'enabled', ?, ?, ?, ?, FALSE)
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object[]> batchArgs = agentIds.stream()
                .map(agentId -> new Object[]{roleId, agentId, timestamp, operatorId, timestamp, operatorId})
                .toList();
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    private SqlAndParams buildRoleQuerySql(String baseSql, String roleCode, String roleName, String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (roleCode != null && !roleCode.isBlank()) {
            sql.append(" AND role_code ILIKE ?");
            params.add("%" + roleCode.trim() + "%");
        }
        if (roleName != null && !roleName.isBlank()) {
            sql.append(" AND role_name ILIKE ?");
            params.add("%" + roleName.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private String buildInClausePlaceholders(Collection<?> values) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Object ignored : values) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    /**
     * SQL 与参数。
     *
     * @param sql    SQL 语句
     * @param params 参数列表
     */
    private record SqlAndParams(String sql, List<Object> params) {
    }
}
