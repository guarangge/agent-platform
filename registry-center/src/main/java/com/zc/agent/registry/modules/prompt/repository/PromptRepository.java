package com.zc.agent.registry.modules.prompt.repository;

import com.zc.agent.registry.modules.prompt.dto.CreatePromptRequest;
import com.zc.agent.registry.modules.prompt.dto.UpdatePromptRequest;
import com.zc.agent.registry.modules.prompt.model.PromptDetailRecord;
import com.zc.agent.registry.modules.prompt.model.PromptPageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Prompt 仓储。
 */
@Repository
public class PromptRepository {

    private static final RowMapper<PromptPageRecord> PROMPT_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public PromptPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PromptPageRecord(
                    rs.getLong("id"),
                    rs.getString("prompt_code"),
                    rs.getString("prompt_name"),
                    rs.getString("prompt_type"),
                    rs.getString("prompt_category"),
                    rs.getString("version"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<PromptDetailRecord> PROMPT_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public PromptDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PromptDetailRecord(
                    rs.getLong("id"),
                    rs.getString("prompt_code"),
                    rs.getString("prompt_name"),
                    rs.getString("prompt_type"),
                    rs.getString("prompt_category"),
                    rs.getString("description"),
                    rs.getString("content"),
                    rs.getString("version"),
                    rs.getString("status"),
                    rs.getString("metadata"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")),
                    getNullableLong(rs, "created_by"),
                    getNullableLong(rs, "updated_by")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public PromptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计 Prompt 总数。
     */
    public long countPrompts(String promptCode,
                             String promptName,
                             String promptType,
                             String promptCategory,
                             String status) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT COUNT(1)
                FROM prompts
                WHERE is_deleted = FALSE
                """,
                promptCode,
                promptName,
                promptType,
                promptCategory,
                status
        );
        return jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
    }

    /**
     * 分页查询 Prompt。
     */
    public List<PromptPageRecord> pagePrompts(String promptCode,
                                              String promptName,
                                              String promptType,
                                              String promptCategory,
                                              String status,
                                              int offset,
                                              int limit) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT id,
                       prompt_code,
                       prompt_name,
                       prompt_type,
                       prompt_category,
                       version,
                       status,
                       description,
                       created_at
                FROM prompts
                WHERE is_deleted = FALSE
                """,
                promptCode,
                promptName,
                promptType,
                promptCategory,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, PROMPT_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按 ID 查询 Prompt 详情。
     */
    public Optional<PromptDetailRecord> findPromptDetailById(Long promptId) {
        String sql = """
                SELECT id,
                       prompt_code,
                       prompt_name,
                       prompt_type,
                       prompt_category,
                       description,
                       content,
                       version,
                       status,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM prompts
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<PromptDetailRecord> records = jdbcTemplate.query(sql, PROMPT_DETAIL_ROW_MAPPER, promptId);
        return records.stream().findFirst();
    }

    /**
     * 按编码查询 Prompt ID。
     */
    public Optional<Long> findPromptIdByCode(String promptCode) {
        String sql = """
                SELECT id
                FROM prompts
                WHERE prompt_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), promptCode);
        return records.stream().findFirst();
    }

    /**
     * 新增 Prompt。
     */
    public Long insertPrompt(CreatePromptRequest request, String metadataJsonText, Long operatorId) {
        String sql = """
                INSERT INTO prompts (
                    prompt_code,
                    prompt_name,
                    prompt_type,
                    prompt_category,
                    description,
                    content,
                    version,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.promptCode(),
                request.promptName(),
                request.promptType(),
                request.promptCategory(),
                request.description(),
                request.content(),
                request.version(),
                request.status(),
                metadataJsonText,
                request.remark(),
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 编辑 Prompt。
     */
    public int updatePrompt(Long promptId, UpdatePromptRequest request, String metadataJsonText, Long operatorId) {
        String sql = """
                UPDATE prompts
                SET prompt_code = ?,
                    prompt_name = ?,
                    prompt_type = ?,
                    prompt_category = ?,
                    description = ?,
                    content = ?,
                    version = ?,
                    status = ?,
                    metadata = CAST(? AS jsonb),
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(
                sql,
                request.promptCode(),
                request.promptName(),
                request.promptType(),
                request.promptCategory(),
                request.description(),
                request.content(),
                request.version(),
                request.status(),
                metadataJsonText,
                request.remark(),
                now,
                operatorId,
                promptId
        );
    }

    /**
     * 更新 Prompt 状态。
     */
    public int updatePromptStatus(Long promptId, String status, Long operatorId) {
        String sql = """
                UPDATE prompts
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(sql, status, now, operatorId, promptId);
    }

    private SqlAndParams buildQuerySql(String baseSql,
                                       String promptCode,
                                       String promptName,
                                       String promptType,
                                       String promptCategory,
                                       String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (promptCode != null) {
            sql.append(" AND prompt_code ILIKE ?");
            params.add("%" + promptCode + "%");
        }
        if (promptName != null) {
            sql.append(" AND prompt_name ILIKE ?");
            params.add("%" + promptName + "%");
        }
        if (promptType != null) {
            sql.append(" AND prompt_type = ?");
            params.add(promptType);
        }
        if (promptCategory != null) {
            sql.append(" AND prompt_category = ?");
            params.add(promptCategory);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    /**
     * SQL 及参数。
     *
     * @param sql SQL 语句
     * @param params 参数列表
     */
    private record SqlAndParams(String sql, List<Object> params) {
    }
}
