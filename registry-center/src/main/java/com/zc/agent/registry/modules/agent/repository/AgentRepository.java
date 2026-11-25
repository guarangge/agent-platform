package com.zc.agent.registry.modules.agent.repository;

import com.zc.agent.registry.modules.agent.dto.CreateAgentRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentRequest;
import com.zc.agent.registry.modules.agent.model.AgentDetailRecord;
import com.zc.agent.registry.modules.agent.model.AgentPageRecord;
import com.zc.agent.registry.modules.agent.model.AgentToolRecord;
import com.zc.agent.registry.modules.agent.model.AgentWorkflowRecord;
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
 * Agent 仓储。
 */
@Repository
public class AgentRepository {

    private static final RowMapper<AgentPageRecord> AGENT_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentPageRecord(
                    rs.getLong("id"),
                    rs.getString("agent_code"),
                    rs.getString("agent_name"),
                    rs.getString("description"),
                    rs.getString("version"),
                    rs.getString("default_model"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<AgentDetailRecord> AGENT_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentDetailRecord(
                    rs.getLong("id"),
                    rs.getString("agent_code"),
                    rs.getString("agent_name"),
                    rs.getString("description"),
                    rs.getString("version"),
                    rs.getString("default_model"),
                    getNullableLong(rs, "default_prompt_id"),
                    rs.getString("default_prompt_name"),
                    getNullableLong(rs, "decision_prompt_id"),
                    rs.getString("decision_prompt_name"),
                    getNullableLong(rs, "planner_prompt_id"),
                    rs.getString("planner_prompt_name"),
                    rs.getString("tool_scope_mode"),
                    rs.getString("status"),
                    rs.getString("metadata_json_text"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")),
                    getNullableLong(rs, "created_by"),
                    getNullableLong(rs, "updated_by")
            );
        }
    };

    private static final RowMapper<AgentToolRecord> AGENT_TOOL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentToolRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentToolRecord(
                    rs.getLong("tool_id"),
                    rs.getString("tool_code"),
                    rs.getString("tool_name"),
                    rs.getString("tool_type"),
                    rs.getString("tool_category"),
                    rs.getString("runtime_capabilities_json_text"),
                    rs.getString("side_effect_level"),
                    rs.getString("input_schema_json_text"),
                    rs.getString("output_schema_json_text"),
                    rs.getString("provider_type"),
                    rs.getString("transport_type"),
                    rs.getString("provider_code"),
                    rs.getString("provider_name"),
                    rs.getString("provider_instance_code"),
                    rs.getString("provider_instance_name"),
                    rs.getString("environment_code"),
                    rs.getString("endpoint"),
                    rs.getString("auth_type"),
                    rs.getString("auth_config_json_text"),
                    rs.getString("headers_json_text"),
                    getNullableInteger(rs, "provider_timeout_seconds"),
                    getNullableInteger(rs, "retry_times"),
                    rs.getString("operation_name"),
                    rs.getString("operation_type"),
                    rs.getString("invoke_config_json_text"),
                    rs.getString("argument_mapping_json_text"),
                    rs.getString("output_mapping_json_text"),
                    rs.getString("permission_level"),
                    getNullableInteger(rs, "timeout_seconds"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getString("metadata_json_text"),
                    toLocalDateTime(rs.getTimestamp("bound_at"))
            );
        }
    };

    private static final RowMapper<AgentWorkflowRecord> AGENT_WORKFLOW_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentWorkflowRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentWorkflowRecord(
                    rs.getLong("workflow_id"),
                    rs.getString("workflow_code"),
                    rs.getString("workflow_name"),
                    rs.getString("workflow_type"),
                    rs.getString("workflow_category"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getString("metadata_json_text"),
                    toLocalDateTime(rs.getTimestamp("bound_at"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public AgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计 Agent 数量。
     *
     * @param agentCode Agent 编码
     * @param agentName Agent 名称
     * @param status    Agent 状态
     * @param version   Agent 版本
     * @return 总数
     */
    public long countAgents(String agentCode, String agentName, String status, String version) {
        SqlAndParams sqlAndParams = buildAgentQuerySql(
                """
                SELECT COUNT(1)
                FROM agents
                WHERE is_deleted = FALSE
                """,
                agentCode,
                agentName,
                status,
                version
        );
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页查询 Agent。
     *
     * @param agentCode Agent 编码
     * @param agentName Agent 名称
     * @param status    Agent 状态
     * @param version   Agent 版本
     * @param offset    偏移量
     * @param limit     限制条数
     * @return Agent 列表
     */
    public List<AgentPageRecord> pageAgents(String agentCode,
                                            String agentName,
                                            String status,
                                            String version,
                                            int offset,
                                            int limit) {
        SqlAndParams sqlAndParams = buildAgentQuerySql(
                """
                SELECT id, agent_code, agent_name, description, version, default_model, status, created_at
                FROM agents
                WHERE is_deleted = FALSE
                """,
                agentCode,
                agentName,
                status,
                version
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, AGENT_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按 Agent ID 查询详情。
     *
     * @param agentId Agent ID
     * @return Agent 详情
     */
    public Optional<AgentDetailRecord> findAgentDetailById(Long agentId) {
        String sql = """
                SELECT a.id,
                       a.agent_code,
                       a.agent_name,
                       a.description,
                       a.version,
                       a.default_model,
                       a.default_prompt_id,
                       dp.prompt_name AS default_prompt_name,
                       a.decision_prompt_id,
                       rp.prompt_name AS decision_prompt_name,
                       a.planner_prompt_id,
                       pp.prompt_name AS planner_prompt_name,
                       a.tool_scope_mode,
                       a.status,
                       COALESCE(a.metadata::text, '{}'::text) AS metadata_json_text,
                       a.remark,
                       a.created_at,
                       a.updated_at,
                       a.created_by,
                       a.updated_by
                FROM agents a
                LEFT JOIN prompts dp ON dp.id = a.default_prompt_id AND dp.is_deleted = FALSE
                LEFT JOIN prompts rp ON rp.id = a.decision_prompt_id AND rp.is_deleted = FALSE
                LEFT JOIN prompts pp ON pp.id = a.planner_prompt_id AND pp.is_deleted = FALSE
                WHERE a.id = ?
                  AND a.is_deleted = FALSE
                LIMIT 1
                """;
        List<AgentDetailRecord> records = jdbcTemplate.query(sql, AGENT_DETAIL_ROW_MAPPER, agentId);
        return records.stream().findFirst();
    }

    /**
     * 按 Agent 编码查询 Agent ID。
     *
     * @param agentCode Agent 编码
     * @return Agent ID
     */
    public Optional<Long> findAgentIdByAgentCode(String agentCode) {
        String sql = """
                SELECT id
                FROM agents
                WHERE agent_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), agentCode);
        return records.stream().findFirst();
    }

    /**
     * 新增 Agent。
     *
     * @param request    新增请求
     * @param operatorId 操作人
     * @return 新 Agent ID
     */
    public Long insertAgent(CreateAgentRequest request, Long operatorId) {
        String sql = """
                INSERT INTO agents (
                    agent_code,
                    agent_name,
                    description,
                    version,
                    default_model,
                    default_prompt_id,
                    decision_prompt_id,
                    planner_prompt_id,
                    tool_scope_mode,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT), ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.agentCode(),
                request.agentName(),
                request.description(),
                request.version(),
                request.defaultModel(),
                request.defaultPromptId(),
                request.decisionPromptId(),
                request.plannerPromptId(),
                request.toolScopeMode(),
                request.status(),
                request.metadata() == null ? "{}" : request.metadata().toString(),
                request.remark(),
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    /**
     * 编辑 Agent。
     *
     * @param agentId     Agent ID
     * @param request     编辑请求
     * @param operatorId  操作人
     * @return 影响行数
     */
    public int updateAgent(Long agentId, UpdateAgentRequest request, Long operatorId) {
        String sql = """
                UPDATE agents
                SET agent_code = ?,
                    agent_name = ?,
                    description = ?,
                    version = ?,
                    default_model = ?,
                    default_prompt_id = CAST(? AS BIGINT),
                    decision_prompt_id = CAST(? AS BIGINT),
                    planner_prompt_id = CAST(? AS BIGINT),
                    tool_scope_mode = ?,
                    status = ?,
                    metadata = CAST(? AS jsonb),
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
                request.agentCode(),
                request.agentName(),
                request.description(),
                request.version(),
                request.defaultModel(),
                request.defaultPromptId(),
                request.decisionPromptId(),
                request.plannerPromptId(),
                request.toolScopeMode(),
                request.status(),
                request.metadata() == null ? "{}" : request.metadata().toString(),
                request.remark(),
                timestamp,
                operatorId,
                agentId
        );
    }

    /**
     * 更新 Agent 状态。
     *
     * @param agentId     Agent ID
     * @param status      状态
     * @param operatorId  操作人
     * @return 影响行数
     */
    public int updateAgentStatus(Long agentId, String status, Long operatorId) {
        String sql = """
                UPDATE agents
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, status, timestamp, operatorId, agentId);
    }

    /**
     * 查询 Agent 已绑定 Tool。
     *
     * @param agentId Agent ID
     * @return Tool 列表
     */
    public List<AgentToolRecord> findAgentToolsByAgentId(Long agentId) {
        String sql = """
                SELECT t.id AS tool_id,
                       t.tool_code,
                       t.tool_name,
                       t.tool_type,
                       t.tool_category,
                       COALESCE(t.runtime_capabilities::text, '[]'::text) AS runtime_capabilities_json_text,
                       t.side_effect_level,
                       COALESCE(t.input_schema::text, '{}'::text) AS input_schema_json_text,
                       COALESCE(t.output_schema::text, '{}'::text) AS output_schema_json_text,
                       tp.provider_type,
                       tp.transport_type,
                       tp.provider_code,
                       tp.provider_name,
                       tpi.instance_code AS provider_instance_code,
                       tpi.instance_name AS provider_instance_name,
                       tpi.environment_code,
                       tpi.endpoint,
                       tpi.auth_type,
                       COALESCE(tpi.auth_config::text, '{}'::text) AS auth_config_json_text,
                       COALESCE(tpi.headers::text, '{}'::text) AS headers_json_text,
                       tpi.timeout_seconds AS provider_timeout_seconds,
                       tpi.retry_times,
                       tb.operation_name,
                       tb.operation_type,
                       COALESCE(tb.invoke_config::text, '{}'::text) AS invoke_config_json_text,
                       COALESCE(tb.argument_mapping::text, '{}'::text) AS argument_mapping_json_text,
                       COALESCE(tb.output_mapping::text, '{}'::text) AS output_mapping_json_text,
                       t.permission_level,
                       t.timeout_seconds,
                       t.status,
                       t.description,
                       COALESCE(t.metadata::text, '{}'::text) AS metadata_json_text,
                       atr.created_at AS bound_at
                FROM agent_tool_rel atr
                INNER JOIN tools t ON t.id = atr.tool_id
                LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
                LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
                LEFT JOIN tool_provider_instances tpi ON tpi.id = tb.provider_instance_id AND tpi.is_deleted = FALSE
                WHERE atr.agent_id = ?
                  AND atr.is_deleted = FALSE
                  AND t.is_deleted = FALSE
                ORDER BY t.id ASC
                """;
        return jdbcTemplate.query(sql, AGENT_TOOL_ROW_MAPPER, agentId);
    }

    /**
     * 查询 Agent 当前已绑定 Tool ID。
     *
     * @param agentId Agent ID
     * @return Tool ID 列表
     */
    public List<Long> findBoundToolIdsByAgentId(Long agentId) {
        String sql = """
                SELECT tool_id
                FROM agent_tool_rel
                WHERE agent_id = ?
                  AND is_deleted = FALSE
                ORDER BY tool_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("tool_id"), agentId);
    }

    /**
     * 查询存在的 Tool ID。
     *
     * @param toolIds Tool ID 列表
     * @return 已存在 Tool ID 列表
     */
    public List<Long> findExistingToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id
                FROM tools
                WHERE is_deleted = FALSE
                  AND id IN (%s)
                ORDER BY id ASC
                """.formatted(buildInClausePlaceholders(toolIds));
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), toolIds.toArray());
    }

    /**
     * 查询可选 Tool 列表。
     *
     * @return Tool 列表
     */
    public List<AgentToolRecord> findAvailableTools() {
        String sql = """
                SELECT t.id AS tool_id,
                       t.tool_code,
                       t.tool_name,
                       t.tool_type,
                       t.tool_category,
                       COALESCE(t.runtime_capabilities::text, '[]'::text) AS runtime_capabilities_json_text,
                       t.side_effect_level,
                       COALESCE(t.input_schema::text, '{}'::text) AS input_schema_json_text,
                       COALESCE(t.output_schema::text, '{}'::text) AS output_schema_json_text,
                       tp.provider_type,
                       tp.transport_type,
                       tp.provider_code,
                       tp.provider_name,
                       tpi.instance_code AS provider_instance_code,
                       tpi.instance_name AS provider_instance_name,
                       tpi.environment_code,
                       tpi.endpoint,
                       tpi.auth_type,
                       COALESCE(tpi.auth_config::text, '{}'::text) AS auth_config_json_text,
                       COALESCE(tpi.headers::text, '{}'::text) AS headers_json_text,
                       tpi.timeout_seconds AS provider_timeout_seconds,
                       tpi.retry_times,
                       tb.operation_name,
                       tb.operation_type,
                       COALESCE(tb.invoke_config::text, '{}'::text) AS invoke_config_json_text,
                       COALESCE(tb.argument_mapping::text, '{}'::text) AS argument_mapping_json_text,
                       COALESCE(tb.output_mapping::text, '{}'::text) AS output_mapping_json_text,
                       t.permission_level,
                       t.timeout_seconds,
                       t.status,
                       t.description,
                       COALESCE(t.metadata::text, '{}'::text) AS metadata_json_text,
                       NULL::timestamp AS bound_at
                FROM tools t
                LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
                LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
                LEFT JOIN tool_provider_instances tpi ON tpi.id = tb.provider_instance_id AND tpi.is_deleted = FALSE
                WHERE t.is_deleted = FALSE
                ORDER BY t.id ASC
                """;
        return jdbcTemplate.query(sql, AGENT_TOOL_ROW_MAPPER);
    }

    /**
     * 查询 Agent 已绑定 Workflow。
     *
     * @param agentId Agent ID
     * @return Workflow 列表
     */
    public List<AgentWorkflowRecord> findAgentWorkflowsByAgentId(Long agentId) {
        String sql = """
                SELECT w.id AS workflow_id,
                       w.workflow_code,
                       w.workflow_name,
                       w.workflow_type,
                       w.workflow_category,
                       w.status,
                       w.description,
                       COALESCE(w.metadata::text, '{}'::text) AS metadata_json_text,
                       awr.created_at AS bound_at
                FROM agent_workflow_rel awr
                INNER JOIN workflows w ON w.id = awr.workflow_id
                WHERE awr.agent_id = ?
                  AND awr.is_deleted = FALSE
                  AND w.is_deleted = FALSE
                ORDER BY w.id ASC
                """;
        return jdbcTemplate.query(sql, AGENT_WORKFLOW_ROW_MAPPER, agentId);
    }

    /**
     * 查询 Agent 当前已绑定 Workflow ID。
     *
     * @param agentId Agent ID
     * @return Workflow ID 列表
     */
    public List<Long> findBoundWorkflowIdsByAgentId(Long agentId) {
        String sql = """
                SELECT workflow_id
                FROM agent_workflow_rel
                WHERE agent_id = ?
                  AND is_deleted = FALSE
                ORDER BY workflow_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("workflow_id"), agentId);
    }

    /**
     * 查询存在的 Workflow ID。
     *
     * @param workflowIds Workflow ID 列表
     * @return 已存在 Workflow ID 列表
     */
    public List<Long> findExistingWorkflowIds(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id
                FROM workflows
                WHERE is_deleted = FALSE
                  AND id IN (%s)
                ORDER BY id ASC
                """.formatted(buildInClausePlaceholders(workflowIds));
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), workflowIds.toArray());
    }

    /**
     * 查询可选 Workflow 列表。
     *
     * @return Workflow 列表
     */
    public List<AgentWorkflowRecord> findAvailableWorkflows() {
        String sql = """
                SELECT w.id AS workflow_id,
                       w.workflow_code,
                       w.workflow_name,
                       w.workflow_type,
                       w.workflow_category,
                       w.status,
                       w.description,
                       COALESCE(w.metadata::text, '{}'::text) AS metadata_json_text,
                       NULL::timestamp AS bound_at
                FROM workflows w
                WHERE w.is_deleted = FALSE
                ORDER BY w.id ASC
                """;
        return jdbcTemplate.query(sql, AGENT_WORKFLOW_ROW_MAPPER);
    }

    /**
     * 更新 Agent Tool 范围模式。
     *
     * @param agentId       Agent ID
     * @param toolScopeMode Tool 范围模式
     * @param operatorId    操作人
     * @return 影响行数
     */
    public int updateAgentToolScopeMode(Long agentId, String toolScopeMode, Long operatorId) {
        String sql = """
                UPDATE agents
                SET tool_scope_mode = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, toolScopeMode, timestamp, operatorId, agentId);
    }

    /**
     * 逻辑删除 Agent Tool 关系。
     *
     * @param agentId     Agent ID
     * @param toolIds     Tool ID 列表
     * @param operatorId  操作人
     */
    public void softDeleteAgentTools(Long agentId, List<Long> toolIds, Long operatorId) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE agent_tool_rel
                SET is_deleted = TRUE,
                    updated_at = ?,
                    updated_by = ?
                WHERE agent_id = ?
                  AND is_deleted = FALSE
                  AND tool_id IN (%s)
                """.formatted(buildInClausePlaceholders(toolIds));
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object> params = new ArrayList<>();
        params.add(timestamp);
        params.add(operatorId);
        params.add(agentId);
        params.addAll(toolIds);
        jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * 新增 Agent Tool 关系。
     *
     * @param agentId     Agent ID
     * @param toolIds     Tool ID 列表
     * @param operatorId  操作人
     */
    public void insertAgentTools(Long agentId, List<Long> toolIds, Long operatorId) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO agent_tool_rel (
                    agent_id,
                    tool_id,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, FALSE)
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object[]> batchArgs = toolIds.stream()
                .map(toolId -> new Object[]{agentId, toolId, timestamp, operatorId, timestamp, operatorId})
                .toList();
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    /**
     * 逻辑删除 Agent Workflow 关系。
     *
     * @param agentId      Agent ID
     * @param workflowIds  Workflow ID 列表
     * @param operatorId   操作人
     */
    public void softDeleteAgentWorkflows(Long agentId, List<Long> workflowIds, Long operatorId) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE agent_workflow_rel
                SET is_deleted = TRUE,
                    updated_at = ?,
                    updated_by = ?
                WHERE agent_id = ?
                  AND is_deleted = FALSE
                  AND workflow_id IN (%s)
                """.formatted(buildInClausePlaceholders(workflowIds));
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object> params = new ArrayList<>();
        params.add(timestamp);
        params.add(operatorId);
        params.add(agentId);
        params.addAll(workflowIds);
        jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * 新增 Agent Workflow 关系。
     *
     * @param agentId      Agent ID
     * @param workflowIds  Workflow ID 列表
     * @param operatorId   操作人
     */
    public void insertAgentWorkflows(Long agentId, List<Long> workflowIds, Long operatorId) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO agent_workflow_rel (
                    agent_id,
                    workflow_id,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, FALSE)
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        List<Object[]> batchArgs = workflowIds.stream()
                .map(workflowId -> new Object[]{agentId, workflowId, timestamp, operatorId, timestamp, operatorId})
                .toList();
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    private SqlAndParams buildAgentQuerySql(String baseSql,
                                            String agentCode,
                                            String agentName,
                                            String status,
                                            String version) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (agentCode != null && !agentCode.isBlank()) {
            sql.append(" AND agent_code ILIKE ?");
            params.add("%" + agentCode.trim() + "%");
        }
        if (agentName != null && !agentName.isBlank()) {
            sql.append(" AND agent_name ILIKE ?");
            params.add("%" + agentName.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (version != null && !version.isBlank()) {
            sql.append(" AND version = ?");
            params.add(version.trim());
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

    private static Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
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
     * @param sql SQL
     * @param params 参数列表
     */
    private record SqlAndParams(String sql, List<Object> params) {
    }
}
