package com.zc.agent.registry.modules.workflow.repository;

import com.zc.agent.registry.modules.workflow.dto.CreateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.dto.UpdateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.model.WorkflowDetailRecord;
import com.zc.agent.registry.modules.workflow.model.WorkflowPageRecord;
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
 * Workflow 仓储。
 */
@Repository
public class WorkflowRepository {

    private static final RowMapper<WorkflowPageRecord> WORKFLOW_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public WorkflowPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new WorkflowPageRecord(
                    rs.getLong("id"),
                    rs.getString("workflow_code"),
                    rs.getString("workflow_name"),
                    rs.getString("workflow_type"),
                    rs.getString("workflow_category"),
                    rs.getString("version"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<WorkflowDetailRecord> WORKFLOW_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public WorkflowDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new WorkflowDetailRecord(
                    rs.getLong("id"),
                    rs.getString("workflow_code"),
                    rs.getString("workflow_name"),
                    rs.getString("workflow_type"),
                    rs.getString("workflow_category"),
                    rs.getString("description"),
                    rs.getString("version"),
                    rs.getString("definition_json"),
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

    public WorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计 Workflow 总数。
     */
    public long countWorkflows(String workflowCode,
                               String workflowName,
                               String workflowType,
                               String workflowCategory,
                               String version,
                               String status) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT COUNT(1)
                FROM workflows
                WHERE is_deleted = FALSE
                """,
                workflowCode,
                workflowName,
                workflowType,
                workflowCategory,
                version,
                status
        );
        return jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
    }

    /**
     * 分页查询 Workflow。
     */
    public List<WorkflowPageRecord> pageWorkflows(String workflowCode,
                                                  String workflowName,
                                                  String workflowType,
                                                  String workflowCategory,
                                                  String version,
                                                  String status,
                                                  int offset,
                                                  int limit) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT id,
                       workflow_code,
                       workflow_name,
                       workflow_type,
                       workflow_category,
                       version,
                       status,
                       description,
                       created_at
                FROM workflows
                WHERE is_deleted = FALSE
                """,
                workflowCode,
                workflowName,
                workflowType,
                workflowCategory,
                version,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, WORKFLOW_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按 ID 查询 Workflow 详情。
     */
    public Optional<WorkflowDetailRecord> findWorkflowDetailById(Long workflowId) {
        String sql = """
                SELECT id,
                       workflow_code,
                       workflow_name,
                       workflow_type,
                       workflow_category,
                       description,
                       version,
                       COALESCE(definition_json::text, '{}') AS definition_json,
                       status,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM workflows
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<WorkflowDetailRecord> records = jdbcTemplate.query(sql, WORKFLOW_DETAIL_ROW_MAPPER, workflowId);
        return records.stream().findFirst();
    }

    /**
     * 按编码查询 Workflow ID。
     */
    public Optional<Long> findWorkflowIdByCode(String workflowCode) {
        String sql = """
                SELECT id
                FROM workflows
                WHERE workflow_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), workflowCode);
        return records.stream().findFirst();
    }

    /**
     * 新增 Workflow。
     */
    public Long insertWorkflow(CreateWorkflowRequest request,
                               String definitionJsonText,
                               String metadataJsonText,
                               Long operatorId) {
        String sql = """
                INSERT INTO workflows (
                    workflow_code,
                    workflow_name,
                    workflow_type,
                    workflow_category,
                    description,
                    version,
                    definition_json,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.workflowCode(),
                request.workflowName(),
                request.workflowType(),
                request.workflowCategory(),
                request.description(),
                request.version(),
                definitionJsonText,
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
     * 编辑 Workflow。
     */
    public int updateWorkflow(Long workflowId,
                              UpdateWorkflowRequest request,
                              String definitionJsonText,
                              String metadataJsonText,
                              Long operatorId) {
        String sql = """
                UPDATE workflows
                SET workflow_code = ?,
                    workflow_name = ?,
                    workflow_type = ?,
                    workflow_category = ?,
                    description = ?,
                    version = ?,
                    definition_json = CAST(? AS jsonb),
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
                request.workflowCode(),
                request.workflowName(),
                request.workflowType(),
                request.workflowCategory(),
                request.description(),
                request.version(),
                definitionJsonText,
                request.status(),
                metadataJsonText,
                request.remark(),
                now,
                operatorId,
                workflowId
        );
    }

    /**
     * 更新 Workflow 状态。
     */
    public int updateWorkflowStatus(Long workflowId, String status, Long operatorId) {
        String sql = """
                UPDATE workflows
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(sql, status, now, operatorId, workflowId);
    }

    private SqlAndParams buildQuerySql(String baseSql,
                                       String workflowCode,
                                       String workflowName,
                                       String workflowType,
                                       String workflowCategory,
                                       String version,
                                       String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (workflowCode != null) {
            sql.append(" AND workflow_code ILIKE ?");
            params.add("%" + workflowCode + "%");
        }
        if (workflowName != null) {
            sql.append(" AND workflow_name ILIKE ?");
            params.add("%" + workflowName + "%");
        }
        if (workflowType != null) {
            sql.append(" AND workflow_type = ?");
            params.add(workflowType);
        }
        if (workflowCategory != null) {
            sql.append(" AND workflow_category = ?");
            params.add(workflowCategory);
        }
        if (version != null) {
            sql.append(" AND version = ?");
            params.add(version);
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
