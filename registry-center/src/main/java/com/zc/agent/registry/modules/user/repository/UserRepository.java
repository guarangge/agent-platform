package com.zc.agent.registry.modules.user.repository;

import com.zc.agent.registry.modules.user.dto.CreateUserRequest;
import com.zc.agent.registry.modules.user.dto.UpdateUserRequest;
import com.zc.agent.registry.modules.user.model.UserDetailRecord;
import com.zc.agent.registry.modules.user.model.UserPageRecord;
import com.zc.agent.registry.modules.user.model.UserRoleRecord;
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
 * 用户仓储。
 */
@Repository
public class UserRepository {

    private static final RowMapper<UserPageRecord> USER_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserPageRecord(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getString("mobile"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("last_login_at")),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<UserDetailRecord> USER_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserDetailRecord(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getString("mobile"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("last_login_at")),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")),
                    getNullableLong(rs, "created_by"),
                    getNullableLong(rs, "updated_by")
            );
        }
    };

    private static final RowMapper<UserRoleRecord> USER_ROLE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserRoleRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserRoleRecord(
                    rs.getLong("role_id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("bound_at"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计用户数量。
     *
     * @param username    用户名
     * @param displayName 显示名称
     * @param status      用户状态
     * @return 总数
     */
    public long countUsers(String username, String displayName, String status) {
        SqlAndParams sqlAndParams = buildUserQuerySql(
                """
                SELECT COUNT(1)
                FROM users
                WHERE is_deleted = FALSE
                """,
                username,
                displayName,
                status
        );
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页查询用户。
     *
     * @param username    用户名
     * @param displayName 显示名称
     * @param status      用户状态
     * @param offset      偏移量
     * @param limit       限制条数
     * @return 用户列表
     */
    public List<UserPageRecord> pageUsers(String username, String displayName, String status, int offset, int limit) {
        SqlAndParams sqlAndParams = buildUserQuerySql(
                """
                SELECT id, username, display_name, email, mobile, status, last_login_at, created_at
                FROM users
                WHERE is_deleted = FALSE
                """,
                username,
                displayName,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, USER_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按用户 ID 查询详情。
     *
     * @param userId 用户 ID
     * @return 用户详情
     */
    public Optional<UserDetailRecord> findUserDetailById(Long userId) {
        String sql = """
                SELECT id,
                       username,
                       display_name,
                       email,
                       mobile,
                       status,
                       last_login_at,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM users
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<UserDetailRecord> records = jdbcTemplate.query(sql, USER_DETAIL_ROW_MAPPER, userId);
        return records.stream().findFirst();
    }

    /**
     * 按用户名查询用户 ID。
     *
     * @param username 用户名
     * @return 用户 ID
     */
    public Optional<Long> findUserIdByUsername(String username) {
        String sql = """
                SELECT id
                FROM users
                WHERE username = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), username);
        return records.stream().findFirst();
    }

    /**
     * 新增用户。
     *
     * @param request      新增请求
     * @param passwordHash 密码哈希
     * @param operatorId   操作人
     * @return 新用户 ID
     */
    public Long insertUser(CreateUserRequest request, String passwordHash, Long operatorId) {
        String sql = """
                INSERT INTO users (
                    username,
                    password_hash,
                    display_name,
                    email,
                    mobile,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.username(),
                passwordHash,
                request.displayName(),
                request.email(),
                request.mobile(),
                request.status(),
                request.remark(),
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    /**
     * 编辑用户。
     *
     * @param userId     用户 ID
     * @param request    编辑请求
     * @param operatorId 操作人
     * @return 影响行数
     */
    public int updateUser(Long userId, UpdateUserRequest request, Long operatorId) {
        String sql = """
                UPDATE users
                SET display_name = ?,
                    email = ?,
                    mobile = ?,
                    status = ?,
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
                request.displayName(),
                request.email(),
                request.mobile(),
                request.status(),
                request.remark(),
                timestamp,
                operatorId,
                userId
        );
    }

    /**
     * 更新用户状态。
     *
     * @param userId     用户 ID
     * @param status     用户状态
     * @param operatorId 操作人
     * @return 影响行数
     */
    public int updateUserStatus(Long userId, String status, Long operatorId) {
        String sql = """
                UPDATE users
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, status, timestamp, operatorId, userId);
    }

    /**
     * 重置密码。
     *
     * @param userId       用户 ID
     * @param passwordHash 新密码哈希
     * @param operatorId   操作人
     * @return 影响行数
     */
    public int updatePassword(Long userId, String passwordHash, Long operatorId) {
        String sql = """
                UPDATE users
                SET password_hash = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, passwordHash, timestamp, operatorId, userId);
    }

    /**
     * 查询用户已绑定角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    public List<UserRoleRecord> findUserRolesByUserId(Long userId) {
        String sql = """
                SELECT r.id AS role_id,
                       r.role_code,
                       r.role_name,
                       r.status,
                       r.description,
                       ur.created_at AS bound_at
                FROM user_role_rel ur
                INNER JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND ur.status = 'enabled'
                  AND ur.is_deleted = FALSE
                  AND r.is_deleted = FALSE
                ORDER BY r.id ASC
                """;
        return jdbcTemplate.query(sql, USER_ROLE_ROW_MAPPER, userId);
    }

    /**
     * 查询用户当前已绑定角色 ID。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    public List<Long> findBoundRoleIdsByUserId(Long userId) {
        String sql = """
                SELECT role_id
                FROM user_role_rel
                WHERE user_id = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                ORDER BY role_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("role_id"), userId);
    }

    /**
     * 查询存在的角色 ID。
     *
     * @param roleIds 角色 ID 列表
     * @return 已存在角色 ID 列表
     */
    public List<Long> findExistingRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id
                FROM roles
                WHERE is_deleted = FALSE
                  AND id IN (%s)
                ORDER BY id ASC
                """.formatted(buildInClausePlaceholders(roleIds));
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), roleIds.toArray());
    }

    /**
     * 逻辑删除用户角色关系。
     *
     * @param userId     用户 ID
     * @param roleIds    角色 ID 列表
     * @param operatorId 操作人
     */
    public void softDeleteUserRoles(Long userId, List<Long> roleIds, Long operatorId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE user_role_rel
                SET is_deleted = TRUE,
                    updated_at = ?,
                    updated_by = ?
                WHERE user_id = ?
                  AND is_deleted = FALSE
                  AND role_id IN (%s)
                """.formatted(buildInClausePlaceholders(roleIds));
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object> params = new ArrayList<>();
        params.add(timestamp);
        params.add(operatorId);
        params.add(userId);
        params.addAll(roleIds);
        jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * 新增用户角色关系。
     *
     * @param userId     用户 ID
     * @param roleIds    角色 ID 列表
     * @param operatorId 操作人
     */
    public void insertUserRoles(Long userId, List<Long> roleIds, Long operatorId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO user_role_rel (
                    user_id,
                    role_id,
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
        List<Object[]> batchArgs = roleIds.stream()
                .map(roleId -> new Object[]{userId, roleId, timestamp, operatorId, timestamp, operatorId})
                .toList();
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    private SqlAndParams buildUserQuerySql(String baseSql, String username, String displayName, String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            sql.append(" AND username ILIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (displayName != null && !displayName.isBlank()) {
            sql.append(" AND display_name ILIKE ?");
            params.add("%" + displayName.trim() + "%");
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
     * @param sql    SQL
     * @param params 参数列表
     */
    private record SqlAndParams(String sql, List<Object> params) {
    }
}
