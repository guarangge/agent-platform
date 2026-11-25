package com.zc.agent.core.modules.conversation.repository;

import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.ApprovalWorkbenchRecord;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.model.ConversationMemoryRecord;
import com.zc.agent.core.modules.conversation.model.ConversationRecord;
import com.zc.agent.core.modules.conversation.model.GuardrailPolicyHitRecord;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.model.TaskDelegationRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceAnalyticsRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceMetricBucketRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceMetricsRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskMemorySnapshotRecord;
import com.zc.agent.core.modules.conversation.model.TaskOperationsRecord;
import com.zc.agent.core.modules.conversation.model.TaskPlanRecord;
import com.zc.agent.core.modules.conversation.model.TaskPlanStepRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceRecord;
import com.zc.agent.core.modules.conversation.model.UserMemoryRecord;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 会话仓储。
 */
@Repository
public class ConversationRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final RowMapper<ConversationRecord> CONVERSATION_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ConversationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ConversationRecord(
                    rs.getLong("id"),
                    rs.getString("conversation_code"),
                    rs.getLong("user_id"),
                    rs.getLong("agent_id"),
                    rs.getString("agent_code"),
                    rs.getString("agent_name"),
                    rs.getString("default_model"),
                    getNullableLong(rs, "default_prompt_id"),
                    rs.getString("default_prompt_name"),
                    getNullableLong(rs, "decision_prompt_id"),
                    rs.getString("decision_prompt_name"),
                    getNullableLong(rs, "planner_prompt_id"),
                    rs.getString("planner_prompt_name"),
                    rs.getString("title"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("last_message_at")),
                    rs.getString("last_message_preview"),
                    parseJson(rs.getString("metadata_json_text")),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );
        }
    };

    private static final RowMapper<MessageRecord> MESSAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public MessageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MessageRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getString("message_type"),
                    rs.getInt("sequence_no"),
                    rs.getString("content"),
                    rs.getString("response_status"),
                    rs.getString("error_message"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<ConversationMemoryRecord> CONVERSATION_MEMORY_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ConversationMemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ConversationMemoryRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getString("memory_summary"),
                    rs.getString("preference_summary"),
                    rs.getString("last_user_message"),
                    rs.getString("last_assistant_summary"),
                    parseJson(rs.getString("metadata")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );
        }
    };

    private static final RowMapper<UserMemoryRecord> USER_MEMORY_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserMemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserMemoryRecord(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getLong("agent_id"),
                    rs.getString("preference_summary"),
                    rs.getString("attention_summary"),
                    rs.getString("fact_summary"),
                    rs.getString("goal_summary"),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );
        }
    };

    private static final RowMapper<TaskRecord> TASK_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskRecord(
                    rs.getLong("id"),
                    rs.getString("task_code"),
                    rs.getString("trace_id"),
                    rs.getLong("conversation_id"),
                    rs.getLong("message_id"),
                    rs.getLong("agent_id"),
                    getNullableLong(rs, "parent_task_id"),
                    getNullableLong(rs, "root_task_id"),
                    getNullableLong(rs, "trigger_task_step_id"),
                    rs.getString("task_kind"),
                    rs.getString("execution_type"),
                    getNullableInteger(rs, "current_plan_version"),
                    rs.getString("execution_chain_json"),
                    getNullableLong(rs, "prompt_id"),
                    rs.getString("prompt_name"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("started_at")),
                    toLocalDateTime(rs.getTimestamp("finished_at")),
                    rs.getString("error_message"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<TaskOperationsRecord> TASK_OPERATIONS_ROW_MAPPER = (rs, rowNum) ->
            new TaskOperationsRecord(
                    rs.getLong("task_id"),
                    rs.getString("task_code"),
                    rs.getString("run_code"),
                    rs.getLong("conversation_id"),
                    rs.getString("conversation_code"),
                    rs.getString("conversation_title"),
                    rs.getLong("agent_id"),
                    rs.getString("agent_name"),
                    getNullableLong(rs, "parent_task_id"),
                    getNullableLong(rs, "root_task_id"),
                    rs.getString("task_kind"),
                    rs.getString("execution_type"),
                    rs.getString("trace_id"),
                    getNullableInteger(rs, "current_plan_version"),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    rs.getInt("step_count"),
                    rs.getInt("success_step_count"),
                    rs.getInt("failed_step_count"),
                    rs.getInt("approval_count"),
                    rs.getInt("pending_approval_count"),
                    rs.getInt("delegation_count"),
                    toLocalDateTime(rs.getTimestamp("started_at")),
                    toLocalDateTime(rs.getTimestamp("finished_at")),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );

    private static final RowMapper<TaskPlanRecord> TASK_PLAN_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskPlanRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskPlanRecord(
                    rs.getLong("id"),
                    rs.getLong("task_id"),
                    rs.getInt("plan_version"),
                    rs.getString("plan_id"),
                    rs.getString("plan_type"),
                    rs.getString("goal"),
                    rs.getString("status"),
                    rs.getString("assumptions_json"),
                    rs.getString("constraints_json"),
                    rs.getString("risks_json"),
                    getNullableLong(rs, "planner_prompt_id"),
                    rs.getString("planner_prompt_name"),
                    rs.getString("source"),
                    rs.getString("metadata"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<TaskPlanStepRecord> TASK_PLAN_STEP_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskPlanStepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskPlanStepRecord(
                    rs.getLong("id"),
                    rs.getLong("task_plan_id"),
                    rs.getLong("task_id"),
                    rs.getInt("plan_version"),
                    rs.getString("step_id"),
                    rs.getInt("step_no"),
                    rs.getString("step_type"),
                    rs.getString("step_name"),
                    rs.getString("objective"),
                    rs.getString("depends_on_json"),
                    rs.getString("target_tool_code"),
                    getNullableLong(rs, "target_workflow_id"),
                    rs.getString("inputs_json"),
                    rs.getString("expected_output"),
                    rs.getString("failure_policy"),
                    rs.getBoolean("replannable"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<TaskMemorySnapshotRecord> TASK_MEMORY_SNAPSHOT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskMemorySnapshotRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskMemorySnapshotRecord(
                    rs.getLong("id"),
                    rs.getLong("task_id"),
                    rs.getLong("conversation_id"),
                    rs.getString("memory_summary"),
                    rs.getString("key_findings"),
                    rs.getString("final_response_summary"),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );
        }
    };

    private static final RowMapper<TaskStepRecord> TASK_STEP_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskStepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskStepRecord(
                    rs.getLong("id"),
                    rs.getLong("task_id"),
                    rs.getInt("step_no"),
                    rs.getString("step_type"),
                    rs.getString("step_name"),
                    rs.getString("status"),
                    rs.getInt("retry_count"),
                    rs.getString("input_snapshot"),
                    rs.getString("output_snapshot"),
                    rs.getString("error_message"),
                    toLocalDateTime(rs.getTimestamp("started_at")),
                    toLocalDateTime(rs.getTimestamp("finished_at")),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<TaskTraceRecord> TASK_TRACE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskTraceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskTraceRecord(
                    rs.getLong("id"),
                    rs.getLong("task_id"),
                    rs.getString("trace_id"),
                    getNullableLong(rs, "task_step_id"),
                    rs.getString("event_type"),
                    rs.getString("span_name"),
                    rs.getString("trace_status"),
                    rs.getString("input_snapshot"),
                    rs.getString("output_snapshot"),
                    rs.getString("error_message"),
                    toLocalDateTime(rs.getTimestamp("started_at")),
                    toLocalDateTime(rs.getTimestamp("finished_at")),
                    getNullableLong(rs, "duration_ms"),
                    rs.getString("metadata"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<ApprovalRecord> APPROVAL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ApprovalRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ApprovalRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getLong("task_id"),
                    rs.getLong("task_step_id"),
                    rs.getString("node_id"),
                    rs.getString("node_name"),
                    rs.getString("status"),
                    rs.getString("approval_comment"),
                    getNullableLong(rs, "approved_by"),
                    toLocalDateTime(rs.getTimestamp("approved_at")),
                    rs.getString("metadata"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<GuardrailPolicyHitRecord> GUARDRAIL_POLICY_HIT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public GuardrailPolicyHitRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GuardrailPolicyHitRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getLong("task_id"),
                    getNullableLong(rs, "task_step_id"),
                    rs.getLong("agent_id"),
                    rs.getString("trace_id"),
                    rs.getString("policy_code"),
                    rs.getString("policy_name"),
                    getNullableInteger(rs, "policy_version"),
                    rs.getString("decision_type"),
                    rs.getString("resource_kind"),
                    getNullableLong(rs, "resource_id"),
                    rs.getString("resource_code"),
                    rs.getString("resource_name"),
                    rs.getString("hit_detail"),
                    rs.getString("metadata"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<ApprovalWorkbenchRecord> APPROVAL_WORKBENCH_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ApprovalWorkbenchRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ApprovalWorkbenchRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getString("conversation_code"),
                    rs.getLong("task_id"),
                    rs.getString("task_code"),
                    rs.getLong("agent_id"),
                    rs.getString("agent_name"),
                    rs.getObject("task_step_id", Long.class),
                    rs.getString("node_id"),
                    rs.getString("node_name"),
                    rs.getString("status"),
                    rs.getString("approval_comment"),
                    rs.getObject("approved_by", Long.class),
                    rs.getObject("approved_at", LocalDateTime.class),
                    rs.getString("metadata"),
                    rs.getObject("created_at", LocalDateTime.class)
            );
        }
    };

    private static final RowMapper<TaskDelegationRecord> TASK_DELEGATION_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskDelegationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskDelegationRecord(
                    rs.getLong("id"),
                    rs.getLong("parent_task_id"),
                    rs.getLong("parent_task_step_id"),
                    rs.getLong("child_task_id"),
                    rs.getLong("conversation_id"),
                    rs.getLong("delegate_agent_id"),
                    rs.getString("delegate_agent_code"),
                    rs.getString("delegate_agent_name"),
                    rs.getString("delegate_instruction"),
                    rs.getString("trace_id"),
                    rs.getString("status"),
                    rs.getString("input_snapshot"),
                    rs.getString("output_snapshot"),
                    rs.getString("error_message"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );
        }
    };

    private static final RowMapper<TaskTraceAnalyticsRecord> TASK_TRACE_ANALYTICS_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskTraceAnalyticsRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskTraceAnalyticsRecord(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    rs.getString("conversation_code"),
                    rs.getLong("task_id"),
                    rs.getString("task_code"),
                    rs.getLong("agent_id"),
                    rs.getString("agent_name"),
                    rs.getString("trace_id"),
                    getNullableLong(rs, "task_step_id"),
                    getNullableInteger(rs, "step_no"),
                    rs.getString("step_name"),
                    rs.getString("event_type"),
                    rs.getString("span_name"),
                    rs.getString("trace_status"),
                    getNullableLong(rs, "duration_ms"),
                    rs.getString("error_message"),
                    rs.getString("input_snapshot"),
                    rs.getString("output_snapshot"),
                    rs.getString("metadata"),
                    toLocalDateTime(rs.getTimestamp("started_at")),
                    toLocalDateTime(rs.getTimestamp("finished_at")),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<TaskTraceMetricBucketRecord> TASK_TRACE_METRIC_BUCKET_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskTraceMetricBucketRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskTraceMetricBucketRecord(
                    rs.getString("bucket_key"),
                    rs.getString("bucket_label"),
                    rs.getLong("count_value")
            );
        }
    };

    private static final RowMapper<TaskTraceMetricsRecord> TASK_TRACE_METRICS_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TaskTraceMetricsRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskTraceMetricsRecord(
                    rs.getLong("total_count"),
                    rs.getLong("trace_count"),
                    rs.getLong("failed_count"),
                    getNullableLong(rs, "avg_duration_ms")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计会话数量。
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @return 会话总数
     */
    public long countConversations(Long userId, Long agentId) {
        SqlAndParams sqlAndParams = buildConversationPageSql(
                """
                SELECT COUNT(1)
                FROM conversations
                WHERE user_id = ?
                  AND is_deleted = FALSE
                """,
                userId,
                agentId
        );
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页查询会话。
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @param offset  偏移量
     * @param limit   条数
     * @return 会话列表
     */
    public List<ConversationRecord> pageConversations(Long userId, Long agentId, int offset, int limit) {
        SqlAndParams sqlAndParams = buildConversationPageSql(
                """
                SELECT id,
                       conversation_code,
                       user_id,
                       agent_id,
                       agent_code,
                       agent_name,
                       default_model,
                       default_prompt_id,
                       default_prompt_name,
                       decision_prompt_id,
                       decision_prompt_name,
                       planner_prompt_id,
                       planner_prompt_name,
                       title,
                       status,
                       last_message_at,
                       last_message_preview,
                       COALESCE(metadata::text, '{}'::text) AS metadata_json_text,
                       created_at,
                       updated_at
                FROM conversations
                WHERE user_id = ?
                  AND is_deleted = FALSE
                """,
                userId,
                agentId
        );
        String sql = sqlAndParams.sql() + " ORDER BY COALESCE(last_message_at, created_at) DESC, id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, CONVERSATION_ROW_MAPPER, params.toArray());
    }

    /** 统计当前用户可见的任务运行记录。 */
    public long countTaskOperations(Long userId, String status, Long agentId, Long conversationId, Long taskId, String keyword) {
        SqlAndParams query = buildTaskOperationsFilter("""
                SELECT COUNT(1)
                FROM tasks task
                JOIN conversations conversation ON conversation.id = task.conversation_id
                WHERE conversation.user_id = ?
                  AND conversation.is_deleted = FALSE
                  AND task.is_deleted = FALSE
                """, userId, status, agentId, conversationId, taskId, keyword);
        Long total = jdbcTemplate.queryForObject(query.sql(), Long.class, query.params().toArray());
        return total == null ? 0L : total;
    }

    /** 分页查询当前用户可见的任务运行摘要。 */
    public List<TaskOperationsRecord> pageTaskOperations(Long userId,
                                                         String status,
                                                         Long agentId,
                                                         Long conversationId,
                                                         Long taskId,
                                                         String keyword,
                                                         int offset,
                                                         int limit) {
        SqlAndParams query = buildTaskOperationsFilter("""
                SELECT task.id AS task_id,
                       task.task_code,
                       task.run_code,
                       task.conversation_id,
                       conversation.conversation_code,
                       conversation.title AS conversation_title,
                       task.agent_id,
                       COALESCE((
                           SELECT delegation.delegate_agent_name
                           FROM task_delegations delegation
                           WHERE delegation.child_task_id = task.id AND delegation.is_deleted = FALSE
                           ORDER BY delegation.id DESC LIMIT 1
                       ), conversation.agent_name) AS agent_name,
                       task.parent_task_id,
                       task.root_task_id,
                       task.task_kind,
                       task.execution_type,
                       task.trace_id,
                       task.current_plan_version,
                       task.status,
                       task.error_message,
                       task.started_at,
                       task.finished_at,
                       task.created_at,
                       (SELECT COUNT(1) FROM task_steps step WHERE step.task_id = task.id AND step.is_deleted = FALSE) AS step_count,
                       (SELECT COUNT(1) FROM task_steps step WHERE step.task_id = task.id AND step.is_deleted = FALSE AND step.status = 'success') AS success_step_count,
                       (SELECT COUNT(1) FROM task_steps step WHERE step.task_id = task.id AND step.is_deleted = FALSE AND step.status = 'failed') AS failed_step_count,
                       (SELECT COUNT(1) FROM approvals approval WHERE approval.task_id = task.id AND approval.is_deleted = FALSE) AS approval_count,
                       (SELECT COUNT(1) FROM approvals approval WHERE approval.task_id = task.id AND approval.is_deleted = FALSE AND approval.status = 'pending') AS pending_approval_count,
                       (SELECT COUNT(1) FROM task_delegations delegation WHERE delegation.parent_task_id = task.id AND delegation.is_deleted = FALSE) AS delegation_count
                FROM tasks task
                JOIN conversations conversation ON conversation.id = task.conversation_id
                WHERE conversation.user_id = ?
                  AND conversation.is_deleted = FALSE
                  AND task.is_deleted = FALSE
                """, userId, status, agentId, conversationId, taskId, keyword);
        String sql = query.sql() + " ORDER BY COALESCE(task.started_at, task.created_at) DESC, task.id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(query.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, TASK_OPERATIONS_ROW_MAPPER, params.toArray());
    }

    /**
     * 按 ID 查询用户会话详情。
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @return 会话详情
     */
    public Optional<ConversationRecord> findConversationById(Long conversationId, Long userId) {
        String sql = """
                SELECT id,
                       conversation_code,
                       user_id,
                       agent_id,
                       agent_code,
                       agent_name,
                       default_model,
                       default_prompt_id,
                       default_prompt_name,
                       decision_prompt_id,
                       decision_prompt_name,
                       planner_prompt_id,
                       planner_prompt_name,
                       title,
                       status,
                       last_message_at,
                       last_message_preview,
                       COALESCE(metadata::text, '{}'::text) AS metadata_json_text,
                       created_at,
                       updated_at
                FROM conversations
                WHERE id = ?
                  AND user_id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ConversationRecord> records = jdbcTemplate.query(sql, CONVERSATION_ROW_MAPPER, conversationId, userId);
        return records.stream().findFirst();
    }

    /**
     * 新增会话。
     *
     * @param conversationCode 会话编码
     * @param userId           用户 ID
     * @param agentSnapshot    Agent 快照
     * @param title            标题
     * @param metadataJson     会话元数据
     * @param operatorId       操作人
     * @return 会话 ID
     */
    public Long insertConversation(String conversationCode,
                                   Long userId,
                                   AgentSnapshot agentSnapshot,
                                   String title,
                                   String metadataJson,
                                   Long operatorId) {
        String sql = """
                INSERT INTO conversations (
                    conversation_code,
                    user_id,
                    agent_id,
                    agent_code,
                    agent_name,
                    default_model,
                    default_prompt_id,
                    default_prompt_name,
                    decision_prompt_id,
                    decision_prompt_name,
                    planner_prompt_id,
                    planner_prompt_name,
                    title,
                    status,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS BIGINT), ?, CAST(? AS BIGINT), ?, CAST(? AS BIGINT), ?, ?, 'pending', CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                conversationCode,
                userId,
                agentSnapshot.agentId(),
                agentSnapshot.agentCode(),
                agentSnapshot.agentName(),
                agentSnapshot.defaultModel(),
                agentSnapshot.defaultPromptId(),
                agentSnapshot.defaultPromptName(),
                agentSnapshot.decisionPromptId(),
                agentSnapshot.decisionPromptName(),
                agentSnapshot.plannerPromptId(),
                agentSnapshot.plannerPromptName(),
                title,
                metadataJson == null ? "{}" : metadataJson,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 查询会话消息。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    public List<MessageRecord> findMessagesByConversationId(Long conversationId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       message_type,
                       sequence_no,
                       content,
                       response_status,
                       error_message,
                       created_at
                FROM messages
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                ORDER BY sequence_no ASC, id ASC
                """;
        return jdbcTemplate.query(sql, MESSAGE_ROW_MAPPER, conversationId);
    }

    /**
     * 查询指定序号之前的最近消息。
     *
     * @param conversationId       会话 ID
     * @param sequenceNoExclusive   排除的最小序号
     * @param limit                返回条数上限
     * @return 最近消息列表
     */
    public List<MessageRecord> findRecentMessagesByConversationIdBeforeSequenceNo(Long conversationId,
                                                                                  int sequenceNoExclusive,
                                                                                  int limit) {
        String sql = """
                SELECT id,
                       conversation_id,
                       message_type,
                       sequence_no,
                       content,
                       response_status,
                       error_message,
                       created_at
                FROM messages
                WHERE conversation_id = ?
                  AND sequence_no < ?
                  AND response_status = 'success'
                  AND is_deleted = FALSE
                ORDER BY sequence_no DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, MESSAGE_ROW_MAPPER, conversationId, sequenceNoExclusive, limit);
    }

    /**
     * 查询下一个消息序号。
     *
     * @param conversationId 会话 ID
     * @return 下一个序号
     */
    public int nextSequenceNo(Long conversationId) {
        String sql = """
                SELECT COALESCE(MAX(sequence_no), 0) + 1
                FROM messages
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                """;
        Integer nextSequenceNo = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return nextSequenceNo == null ? 1 : nextSequenceNo;
    }

    /**
     * 新增消息。
     *
     * @param conversationId 会话 ID
     * @param messageType    消息类型
     * @param sequenceNo     顺序号
     * @param content        内容
     * @param responseStatus 响应状态
     * @param errorMessage   错误信息
     * @param operatorId     操作人
     * @return 消息 ID
     */
    public Long insertMessage(Long conversationId,
                              String messageType,
                              int sequenceNo,
                              String content,
                              String responseStatus,
                              String errorMessage,
                              Long operatorId) {
        String sql = """
                INSERT INTO messages (
                    conversation_id,
                    message_type,
                    sequence_no,
                    content,
                    response_status,
                    error_message,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                conversationId,
                messageType,
                sequenceNo,
                content,
                responseStatus,
                errorMessage,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 原子递增会话消息计数器并写入消息，避免多 Worker 的 MAX+1 快照竞争。
     *
     * @return 消息 ID 和数据库实际分配的顺序号
     */
    public InsertedMessage insertMessageWithAllocatedSequence(Long conversationId,
                                                               String messageType,
                                                               String content,
                                                               String responseStatus,
                                                               String errorMessage,
                                                               Long operatorId) {
        String sql = """
                WITH next_sequence AS (
                    UPDATE conversations
                    SET message_sequence = message_sequence + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND is_deleted = FALSE
                    RETURNING message_sequence AS sequence_no
                )
                INSERT INTO messages (
                    conversation_id, message_type, sequence_no, content, response_status, error_message,
                    metadata, created_at, created_by, updated_at, updated_by, is_deleted
                )
                SELECT ?, ?, next_sequence.sequence_no, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE
                FROM next_sequence
                RETURNING id, sequence_no
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNum) -> new InsertedMessage(
                        resultSet.getLong("id"), resultSet.getInt("sequence_no")),
                conversationId, conversationId, messageType, content, responseStatus, errorMessage,
                now, operatorId, now, operatorId);
    }

    /** 数据库原子写入后的消息标识。 */
    public record InsertedMessage(Long messageId, int sequenceNo) {
    }

    /**
     * 按 ID 查询消息。
     *
     * @param messageId 消息 ID
     * @return 消息记录
     */
    public Optional<MessageRecord> findMessageById(Long messageId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       message_type,
                       sequence_no,
                       content,
                       response_status,
                       error_message,
                       created_at
                FROM messages
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<MessageRecord> records = jdbcTemplate.query(sql, MESSAGE_ROW_MAPPER, messageId);
        return records.stream().findFirst();
    }

    /**
     * 查询指定 Task 对应的最终助手消息。
     * 通过 Task 的用户消息序号定位后续第一条助手消息，避免并发会话时读取其他 Run 的结果。
     */
    public Optional<MessageRecord> findAssistantMessageByTaskId(Long taskId) {
        String sql = """
                SELECT assistant.id,
                       assistant.conversation_id,
                       assistant.message_type,
                       assistant.sequence_no,
                       assistant.content,
                       assistant.response_status,
                       assistant.error_message,
                       assistant.created_at
                FROM tasks task
                JOIN messages user_message
                  ON user_message.id = task.message_id
                 AND user_message.is_deleted = FALSE
                JOIN messages assistant
                  ON assistant.conversation_id = task.conversation_id
                 AND assistant.message_type = 'assistant'
                 AND assistant.sequence_no > user_message.sequence_no
                 AND assistant.response_status = 'success'
                 AND assistant.is_deleted = FALSE
                WHERE task.id = ?
                  AND task.is_deleted = FALSE
                ORDER BY assistant.sequence_no ASC, assistant.id ASC
                LIMIT 1
                """;
        List<MessageRecord> records = jdbcTemplate.query(sql, MESSAGE_ROW_MAPPER, taskId);
        return records.stream().findFirst();
    }

    /**
     * 查询会话记忆。
     *
     * @param conversationId 会话 ID
     * @return 会话记忆
     */
    public Optional<ConversationMemoryRecord> findConversationMemoryByConversationId(Long conversationId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       memory_summary,
                       preference_summary,
                       last_user_message,
                       last_assistant_summary,
                       COALESCE(metadata::text, '{}') AS metadata,
                       updated_at
                FROM conversation_memories
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ConversationMemoryRecord> records = jdbcTemplate.query(sql, CONVERSATION_MEMORY_ROW_MAPPER, conversationId);
        return records.stream().findFirst();
    }

    /**
     * 写入或更新会话记忆。
     *
     * @param conversationId       会话 ID
     * @param memorySummary        记忆摘要
     * @param preferenceSummary    偏好摘要
     * @param lastUserMessage      最近用户消息摘要
     * @param lastAssistantSummary 最近助手响应摘要
     * @param operatorId           操作人
     */
    public void upsertConversationMemory(Long conversationId,
                                         String memorySummary,
                                         String preferenceSummary,
                                         String lastUserMessage,
                                         String lastAssistantSummary,
                                         Long operatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String updateSql = """
                UPDATE conversation_memories
                SET memory_summary = ?,
                    preference_summary = ?,
                    last_user_message = ?,
                    last_assistant_summary = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                """;
        int updatedRows = jdbcTemplate.update(
                updateSql,
                memorySummary,
                preferenceSummary,
                lastUserMessage,
                lastAssistantSummary,
                now,
                operatorId,
                conversationId
        );
        if (updatedRows > 0) {
            return;
        }
        String insertSql = """
                INSERT INTO conversation_memories (
                    conversation_id,
                    memory_summary,
                    preference_summary,
                    last_user_message,
                    last_assistant_summary,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                """;
        jdbcTemplate.update(
                insertSql,
                conversationId,
                memorySummary,
                preferenceSummary,
                lastUserMessage,
                lastAssistantSummary,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 更新会话记忆扩展元数据。
     *
     * @param conversationId 会话 ID
     * @param metadataJson   元数据 JSON
     * @param operatorId     操作人
     */
    public void updateConversationMemoryMetadata(Long conversationId,
                                                 String metadataJson,
                                                 Long operatorId) {
        String sql = """
                UPDATE conversation_memories
                SET metadata = CAST(? AS jsonb),
                    updated_at = ?,
                    updated_by = ?
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, metadataJson == null ? "{}" : metadataJson, now, operatorId, conversationId);
    }

    /**
     * 查询用户记忆。
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @return 用户记忆
     */
    public Optional<UserMemoryRecord> findUserMemoryByUserIdAndAgentId(Long userId, Long agentId) {
        String sql = """
                SELECT id,
                       user_id,
                       agent_id,
                       preference_summary,
                       attention_summary,
                       fact_summary,
                       goal_summary,
                       updated_at
                FROM user_memories
                WHERE user_id = ?
                  AND agent_id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<UserMemoryRecord> records = jdbcTemplate.query(sql, USER_MEMORY_ROW_MAPPER, userId, agentId);
        return records.stream().findFirst();
    }

    /**
     * 写入或更新用户记忆。
     *
     * @param userId            用户 ID
     * @param agentId           Agent ID
     * @param preferenceSummary 偏好摘要
     * @param attentionSummary  关注点摘要
     * @param factSummary       长期事实摘要
     * @param goalSummary       长期目标摘要
     * @param operatorId        操作人
     */
    public void upsertUserMemory(Long userId,
                                 Long agentId,
                                 String preferenceSummary,
                                 String attentionSummary,
                                 String factSummary,
                                 String goalSummary,
                                 Long operatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String updateSql = """
                UPDATE user_memories
                SET preference_summary = ?,
                    attention_summary = ?,
                    fact_summary = ?,
                    goal_summary = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE user_id = ?
                  AND agent_id = ?
                  AND is_deleted = FALSE
                """;
        int updatedRows = jdbcTemplate.update(
                updateSql,
                preferenceSummary,
                attentionSummary,
                factSummary,
                goalSummary,
                now,
                operatorId,
                userId,
                agentId
        );
        if (updatedRows > 0) {
            return;
        }
        String insertSql = """
                INSERT INTO user_memories (
                    user_id,
                    agent_id,
                    preference_summary,
                    attention_summary,
                    fact_summary,
                    goal_summary,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                """;
        jdbcTemplate.update(
                insertSql,
                userId,
                agentId,
                preferenceSummary,
                attentionSummary,
                factSummary,
                goalSummary,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 查询任务记忆快照。
     *
     * @param taskId 任务 ID
     * @return 任务记忆快照
     */
    public Optional<TaskMemorySnapshotRecord> findTaskMemorySnapshotByTaskId(Long taskId) {
        String sql = """
                SELECT id,
                       task_id,
                       conversation_id,
                       memory_summary,
                       key_findings,
                       final_response_summary,
                       updated_at
                FROM task_memory_snapshots
                WHERE task_id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<TaskMemorySnapshotRecord> records = jdbcTemplate.query(sql, TASK_MEMORY_SNAPSHOT_ROW_MAPPER, taskId);
        return records.stream().findFirst();
    }

    /**
     * 写入或更新任务记忆快照。
     *
     * @param taskId                任务 ID
     * @param conversationId        会话 ID
     * @param memorySummary         任务记忆摘要
     * @param keyFindings           关键发现
     * @param finalResponseSummary  最终响应摘要
     * @param operatorId            操作人
     */
    public void upsertTaskMemorySnapshot(Long taskId,
                                         Long conversationId,
                                         String memorySummary,
                                         String keyFindings,
                                         String finalResponseSummary,
                                         Long operatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String updateSql = """
                UPDATE task_memory_snapshots
                SET memory_summary = ?,
                    key_findings = ?,
                    final_response_summary = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE task_id = ?
                  AND is_deleted = FALSE
                """;
        int updatedRows = jdbcTemplate.update(
                updateSql,
                memorySummary,
                keyFindings,
                finalResponseSummary,
                now,
                operatorId,
                taskId
        );
        if (updatedRows > 0) {
            return;
        }
        String insertSql = """
                INSERT INTO task_memory_snapshots (
                    task_id,
                    conversation_id,
                    memory_summary,
                    key_findings,
                    final_response_summary,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                """;
        jdbcTemplate.update(
                insertSql,
                taskId,
                conversationId,
                memorySummary,
                keyFindings,
                finalResponseSummary,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 更新会话状态和最近消息信息。
     *
     * @param conversationId     会话 ID
     * @param status             状态
     * @param lastMessagePreview 最近消息摘要
     * @param title              标题
     * @param operatorId         操作人
     */
    public void updateConversationRuntime(Long conversationId,
                                          String status,
                                          String lastMessagePreview,
                                          String title,
                                          Long operatorId) {
        String sql = """
                UPDATE conversations
                SET status = ?,
                    last_message_at = ?,
                    last_message_preview = ?,
                    title = COALESCE(?, title),
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, status, now, lastMessagePreview, title, now, operatorId, conversationId);
    }

    /**
     * 更新指定用户拥有的会话标题。
     *
     * @param conversationId 会话 ID
     * @param userId         会话所有者 ID
     * @param title          新标题
     * @param operatorId     操作人 ID
     */
    public void updateConversationTitle(Long conversationId,
                                        Long userId,
                                        String title,
                                        Long operatorId) {
        String sql = """
                UPDATE conversations
                SET title = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND user_id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, title, now, operatorId, conversationId, userId);
    }

    /**
     * 更新会话元数据。
     *
     * @param conversationId 会话 ID
     * @param metadataJson   元数据 JSON
     * @param operatorId     操作人
     */
    public void updateConversationMetadata(Long conversationId,
                                           String metadataJson,
                                           Long operatorId) {
        String sql = """
                UPDATE conversations
                SET metadata = CAST(? AS jsonb),
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, metadataJson == null ? "{}" : metadataJson, now, operatorId, conversationId);
    }

    /** 原子更新当前用户会话的流式响应偏好，避免覆盖并发写入的其他 metadata。 */
    public void updateConversationStreamingPreference(Long conversationId,
                                                      Long userId,
                                                      boolean enabled,
                                                      Long operatorId) {
        String sql = """
                UPDATE conversations
                SET metadata = jsonb_set(
                        COALESCE(metadata, '{}'::jsonb),
                        '{interactionPolicy}',
                        COALESCE(metadata -> 'interactionPolicy', '{}'::jsonb)
                            || jsonb_build_object('responseStreamingEnabled', CAST(? AS boolean)),
                        TRUE
                    ),
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND user_id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, enabled, now, operatorId, conversationId, userId);
    }

    /**
     * 新增任务。
     *
     * @param taskCode         任务编码
     * @param conversationId   会话 ID
     * @param messageId        用户消息 ID
     * @param agentSnapshot    Agent 快照
     * @param operatorId       操作人
     * @return 任务 ID
     */
    public Long insertTask(String taskCode,
                           String traceId,
                           Long conversationId,
                           Long messageId,
                           AgentSnapshot agentSnapshot,
                           Long operatorId) {
        return insertTask(
                taskCode,
                traceId,
                conversationId,
                messageId,
                agentSnapshot.agentId(),
                agentSnapshot.defaultPromptId(),
                agentSnapshot.defaultPromptName(),
                null,
                null,
                null,
                "primary",
                operatorId
        );
    }

    public Long insertTask(String taskCode,
                           String traceId,
                           Long conversationId,
                           Long messageId,
                           Long agentId,
                           Long promptId,
                           String promptName,
                           Long parentTaskId,
                           Long rootTaskId,
                           Long triggerTaskStepId,
                           String taskKind,
                           Long operatorId) {
        String sql = """
                INSERT INTO tasks (
                    task_code,
                    trace_id,
                    conversation_id,
                    message_id,
                    agent_id,
                    parent_task_id,
                    root_task_id,
                    trigger_task_step_id,
                    task_kind,
                    prompt_id,
                    prompt_name,
                    status,
                    started_at,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT), ?, CAST(? AS BIGINT), ?, 'processing', ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                taskCode,
                traceId,
                conversationId,
                messageId,
                agentId,
                parentTaskId,
                rootTaskId,
                triggerTaskStepId,
                taskKind,
                promptId,
                promptName,
                now,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 更新任务状态。
     *
     * @param taskId        任务 ID
     * @param status        状态
     * @param errorMessage  错误信息
     * @param operatorId    操作人
     */
    public void updateTaskStatus(Long taskId, String status, String errorMessage, Long operatorId) {
        String sql = """
                UPDATE tasks
                SET status = ?,
                    finished_at = CASE WHEN ? IN ('success', 'failed') THEN ? ELSE finished_at END,
                    error_message = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, status, status, now, errorMessage, now, operatorId, taskId);
    }

    /**
     * 更新任务执行分类。
     *
     * @param taskId        任务 ID
     * @param executionType 执行分类
     * @param operatorId    操作人
     */
    public void updateTaskExecutionType(Long taskId, String executionType, Long operatorId) {
        String sql = """
                UPDATE tasks
                SET execution_type = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, executionType, now, operatorId, taskId);
    }

    /**
     * 更新任务当前计划版本。
     *
     * @param taskId              任务 ID
     * @param currentPlanVersion  当前计划版本
     * @param operatorId          操作人
     */
    public void updateTaskCurrentPlanVersion(Long taskId, Integer currentPlanVersion, Long operatorId) {
        String sql = """
                UPDATE tasks
                   SET current_plan_version = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, currentPlanVersion, now, operatorId, taskId);
    }

    public boolean existsTaskPlanId(String planId) {
        String sql = """
                SELECT COUNT(1)
                  FROM task_plans
                 WHERE plan_id = ?
                   AND is_deleted = FALSE
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, planId);
        return count != null && count > 0;
    }

    /**
     * 更新任务调用链路快照。
     *
     * @param taskId              任务 ID
     * @param executionChainJson  调用链路快照
     * @param operatorId          操作人
     */
    public void updateTaskExecutionChain(Long taskId, String executionChainJson, Long operatorId) {
        String sql = """
                UPDATE tasks
                SET execution_chain_json = CAST(? AS jsonb),
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, executionChainJson, now, operatorId, taskId);
    }

    /**
     * 查询任务下一个计划版本号。
     *
     * @param taskId 任务 ID
     * @return 版本号
     */
    public int nextTaskPlanVersion(Long taskId) {
        String sql = """
                SELECT COALESCE(MAX(plan_version), 0) + 1
                FROM task_plans
                WHERE task_id = ?
                  AND is_deleted = FALSE
                """;
        Integer nextVersion = jdbcTemplate.queryForObject(sql, Integer.class, taskId);
        return nextVersion == null ? 1 : nextVersion;
    }

    /**
     * 新增任务计划头。
     *
     * @param taskId             任务 ID
     * @param planVersion        计划版本
     * @param executionPlan      执行计划
     * @param plannerPromptId    规划 Prompt ID
     * @param plannerPromptName  规划 Prompt 名称
     * @param source             计划来源
     * @param assumptionsJson    假设 JSON
     * @param constraintsJson    约束 JSON
     * @param risksJson          风险 JSON
     * @param operatorId         操作人
     * @return 计划主键 ID
     */
    public Long insertTaskPlan(Long taskId,
                               Integer planVersion,
                               ExecutionPlan executionPlan,
                               Long plannerPromptId,
                               String plannerPromptName,
                               String source,
                               String assumptionsJson,
                               String constraintsJson,
                               String risksJson,
                               String metadataJson,
                               Long operatorId) {
        String sql = """
                INSERT INTO task_plans (
                    task_id,
                    plan_version,
                    plan_id,
                    plan_type,
                    goal,
                    status,
                    assumptions_json,
                    constraints_json,
                    risks_json,
                    planner_prompt_id,
                    planner_prompt_name,
                    source,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS BIGINT), ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                taskId,
                planVersion,
                executionPlan.planId(),
                executionPlan.planType(),
                executionPlan.goal(),
                executionPlan.status(),
                assumptionsJson,
                constraintsJson,
                risksJson,
                plannerPromptId,
                plannerPromptName,
                source,
                metadataJson == null ? "{}" : metadataJson,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 新增任务计划步骤。
     *
     * @param taskPlanId        计划主键 ID
     * @param taskId            任务 ID
     * @param planVersion       计划版本
     * @param step              计划步骤
     * @param dependsOnJson     依赖步骤 JSON
     * @param inputsJson        输入 JSON
     * @param operatorId        操作人
     */
    public void insertTaskPlanStep(Long taskPlanId,
                                   Long taskId,
                                   Integer planVersion,
                                   ExecutionPlan.ExecutionPlanStep step,
                                   String dependsOnJson,
                                   String inputsJson,
                                   Long operatorId) {
        String sql = """
                INSERT INTO task_plan_steps (
                    task_plan_id,
                    task_id,
                    plan_version,
                    step_id,
                    step_no,
                    step_type,
                    step_name,
                    objective,
                    depends_on_json,
                    target_tool_code,
                    target_workflow_id,
                    inputs_json,
                    expected_output,
                    failure_policy,
                    replannable,
                    status,
                    description,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS BIGINT),
                          CAST(? AS jsonb), ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(
                sql,
                taskPlanId,
                taskId,
                planVersion,
                step.stepId(),
                step.stepNo(),
                step.stepType(),
                step.stepName(),
                step.objective(),
                dependsOnJson,
                step.targetToolCode(),
                step.targetWorkflowId(),
                inputsJson,
                step.expectedOutput(),
                step.failurePolicy(),
                step.replannable(),
                step.status(),
                step.description(),
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 新增审批记录。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param taskStepId     步骤 ID
     * @param nodeId         节点 ID
     * @param nodeName       节点名称
     * @param operatorId     操作人
     * @return 审批 ID
     */
    public Long insertApproval(Long conversationId,
                               Long taskId,
                               Long taskStepId,
                               String nodeId,
                               String nodeName,
                               String metadataJson,
                               Long operatorId) {
        String sql = """
                INSERT INTO approvals (
                    conversation_id,
                    task_id,
                    task_step_id,
                    node_id,
                    node_name,
                    status,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, 'pending', CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(sql, Long.class,
                conversationId, taskId, taskStepId, nodeId, nodeName, metadataJson == null ? "{}" : metadataJson,
                now, operatorId, now, operatorId);
    }

    /**
     * 查询任务审批列表。
     *
     * @param taskId 任务 ID
     * @return 审批列表
     */
    public List<ApprovalRecord> findApprovalsByTaskId(Long taskId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       task_id,
                       task_step_id,
                       node_id,
                       node_name,
                       status,
                       approval_comment,
                       approved_by,
                       approved_at,
                       COALESCE(metadata::text, '{}') AS metadata,
                       created_at
                FROM approvals
                WHERE task_id = ?
                  AND is_deleted = FALSE
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, APPROVAL_ROW_MAPPER, taskId);
    }

    public List<ApprovalWorkbenchRecord> findApprovalWorkbenchRecords(String status,
                                                                     Long agentId,
                                                                     String conversationCode,
                                                                     String taskCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id,
                       a.conversation_id,
                       c.conversation_code,
                       a.task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       a.task_step_id,
                       a.node_id,
                       a.node_name,
                       a.status,
                       a.approval_comment,
                       a.approved_by,
                       a.approved_at,
                       COALESCE(a.metadata::text, '{}') AS metadata,
                       a.created_at
                FROM approvals a
                JOIN tasks t ON t.id = a.task_id AND t.is_deleted = FALSE
                JOIN conversations c ON c.id = a.conversation_id AND c.is_deleted = FALSE
                WHERE a.is_deleted = FALSE
                """);
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status.trim());
        }
        if (agentId != null) {
            sql.append(" AND t.agent_id = ?");
            params.add(agentId);
        }
        if (conversationCode != null && !conversationCode.isBlank()) {
            sql.append(" AND c.conversation_code ILIKE ?");
            params.add("%" + conversationCode.trim() + "%");
        }
        if (taskCode != null && !taskCode.isBlank()) {
            sql.append(" AND t.task_code ILIKE ?");
            params.add("%" + taskCode.trim() + "%");
        }
        sql.append(" ORDER BY a.created_at DESC, a.id DESC");
        return jdbcTemplate.query(sql.toString(), APPROVAL_WORKBENCH_ROW_MAPPER, params.toArray());
    }

    /**
     * 查询待审批记录。
     *
     * @param approvalId 审批 ID
     * @param taskId     任务 ID
     * @return 审批记录
     */
    public Optional<ApprovalRecord> findPendingApproval(Long approvalId, Long taskId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       task_id,
                       task_step_id,
                       node_id,
                       node_name,
                       status,
                       approval_comment,
                       approved_by,
                       approved_at,
                       COALESCE(metadata::text, '{}') AS metadata,
                       created_at
                FROM approvals
                WHERE id = ?
                  AND task_id = ?
                  AND status = 'pending'
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ApprovalRecord> records = jdbcTemplate.query(sql, APPROVAL_ROW_MAPPER, approvalId, taskId);
        return records.stream().findFirst();
    }

    /**
     * 按审批 ID 查询审批记录。
     *
     * @param approvalId 审批 ID
     * @param taskId     任务 ID
     * @return 审批记录
     */
    public Optional<ApprovalRecord> findApprovalById(Long approvalId, Long taskId) {
        String sql = """
                SELECT id,
                       conversation_id,
                       task_id,
                       task_step_id,
                       node_id,
                       node_name,
                       status,
                       approval_comment,
                       approved_by,
                       approved_at,
                       COALESCE(metadata::text, '{}') AS metadata,
                       created_at
                FROM approvals
                WHERE id = ?
                  AND task_id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ApprovalRecord> records = jdbcTemplate.query(sql, APPROVAL_ROW_MAPPER, approvalId, taskId);
        return records.stream().findFirst();
    }

    /**
     * 更新审批状态。
     *
     * @param approvalId      审批 ID
     * @param status          审批状态
     * @param approvalComment 审批说明
     * @param operatorId      操作人
     */
    public void updateApprovalStatus(Long approvalId,
                                     String status,
                                     String approvalComment,
                                     Long operatorId) {
        String sql = """
                UPDATE approvals
                SET status = ?,
                    approval_comment = ?,
                    approved_by = ?,
                    approved_at = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, status, approvalComment, operatorId, now, now, operatorId, approvalId);
    }

    /**
     * 新增任务步骤。
     *
     * @param taskId          任务 ID
     * @param stepNo          步骤序号
     * @param stepType        步骤类型
     * @param stepName        步骤名称
     * @param inputSnapshot   输入快照
     * @param operatorId      操作人
     * @return 步骤 ID
     */
    public Long insertTaskStep(Long taskId,
                               int stepNo,
                               String stepType,
                               String stepName,
                               String inputSnapshot,
                               Long operatorId) {
        String sql = """
                INSERT INTO task_steps (
                    task_id,
                    step_no,
                    step_type,
                    step_name,
                    status,
                    retry_count,
                    input_snapshot,
                    output_snapshot,
                    started_at,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, 'processing', 0, CAST(? AS jsonb), '{}'::jsonb, ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                taskId,
                stepNo,
                stepType,
                stepName,
                inputSnapshot,
                now,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    /**
     * 更新任务步骤状态。
     *
     * @param taskStepId      步骤 ID
     * @param status          状态
     * @param outputSnapshot  输出快照
     * @param errorMessage    错误信息
     * @param operatorId      操作人
     */
    public void updateTaskStepStatus(Long taskStepId,
                                     String status,
                                     String outputSnapshot,
                                     String errorMessage,
                                     Long operatorId) {
        String sql = """
                UPDATE task_steps
                SET status = ?,
                    output_snapshot = CAST(? AS jsonb),
                    error_message = ?,
                    finished_at = CASE WHEN ? IN ('success', 'failed') THEN ? ELSE finished_at END,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, status, outputSnapshot, errorMessage, status, now, now, operatorId, taskStepId);
    }

    public Long insertTaskTrace(Long taskId,
                                String traceId,
                                Long taskStepId,
                                String eventType,
                                String spanName,
                                String traceStatus,
                                String inputSnapshot,
                                String outputSnapshot,
                                String errorMessage,
                                LocalDateTime startedAt,
                                LocalDateTime finishedAt,
                                Long durationMs,
                                String metadata,
                                Long operatorId) {
        String sql = """
                INSERT INTO task_traces (
                    task_id,
                    trace_id,
                    task_step_id,
                    event_type,
                    span_name,
                    trace_status,
                    input_snapshot,
                    output_snapshot,
                    error_message,
                    started_at,
                    finished_at,
                    duration_ms,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                taskId,
                traceId,
                taskStepId,
                eventType,
                spanName,
                traceStatus,
                inputSnapshot == null ? "{}" : inputSnapshot,
                outputSnapshot == null ? "{}" : outputSnapshot,
                errorMessage,
                Timestamp.valueOf(startedAt),
                Timestamp.valueOf(finishedAt),
                durationMs == null ? 0L : durationMs,
                metadata == null ? "{}" : metadata,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    public Long insertTaskDelegation(Long parentTaskId,
                                     Long parentTaskStepId,
                                     Long childTaskId,
                                     Long conversationId,
                                     Long delegateAgentId,
                                     String delegateAgentCode,
                                     String delegateAgentName,
                                     String delegateInstruction,
                                     String traceId,
                                     String inputSnapshot,
                                     Long operatorId) {
        String sql = """
                INSERT INTO task_delegations (
                    parent_task_id,
                    parent_task_step_id,
                    child_task_id,
                    conversation_id,
                    delegate_agent_id,
                    delegate_agent_code,
                    delegate_agent_name,
                    delegate_instruction,
                    trace_id,
                    status,
                    input_snapshot,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'processing', CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                parentTaskId,
                parentTaskStepId,
                childTaskId,
                conversationId,
                delegateAgentId,
                delegateAgentCode,
                delegateAgentName,
                delegateInstruction,
                traceId,
                inputSnapshot == null ? "{}" : inputSnapshot,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    public void updateTaskDelegationStatus(Long delegationId,
                                           String status,
                                           String outputSnapshot,
                                           String errorMessage,
                                           Long operatorId) {
        String sql = """
                UPDATE task_delegations
                SET status = ?,
                    output_snapshot = CAST(? AS jsonb),
                    error_message = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, status, outputSnapshot == null ? "{}" : outputSnapshot, errorMessage, now, operatorId, delegationId);
    }

    public List<TaskDelegationRecord> findTaskDelegationsByConversationId(Long conversationId) {
        String sql = """
                SELECT id,
                       parent_task_id,
                       parent_task_step_id,
                       child_task_id,
                       conversation_id,
                       delegate_agent_id,
                       delegate_agent_code,
                       delegate_agent_name,
                       delegate_instruction,
                       trace_id,
                       status,
                       COALESCE(input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot,
                       error_message,
                       created_at,
                       updated_at
                FROM task_delegations
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                ORDER BY id ASC
                """;
        return jdbcTemplate.query(sql, TASK_DELEGATION_ROW_MAPPER, conversationId);
    }

    /**
     * 递增任务步骤重试次数。
     *
     * @param taskStepId 步骤 ID
     * @param operatorId 操作人
     */
    public void incrementTaskStepRetryCount(Long taskStepId, Long operatorId) {
        String sql = """
                UPDATE task_steps
                SET retry_count = retry_count + 1,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, now, operatorId, taskStepId);
    }

    /**
     * 按 ID 查询任务步骤。
     *
     * @param taskStepId 步骤 ID
     * @return 步骤详情
     */
    public Optional<TaskStepRecord> findTaskStepById(Long taskStepId) {
        String sql = """
                SELECT id,
                       task_id,
                       step_no,
                       step_type,
                       step_name,
                       status,
                       retry_count,
                       COALESCE(input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot,
                       error_message,
                       started_at,
                       finished_at,
                       created_at
                FROM task_steps
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<TaskStepRecord> records = jdbcTemplate.query(sql, TASK_STEP_ROW_MAPPER, taskStepId);
        return records.stream().findFirst();
    }

    /**
     * 软删除指定步骤号及其之后的任务步骤。
     *
     * 恢复执行时，需要为同一 `task_id + step_no` 重新插入步骤记录。
     * 这里先把旧失败步骤及其后续步骤归档为已删除，避免唯一约束冲突。
     *
     * @param taskId      任务 ID
     * @param fromStepNo  起始步骤号
     * @param operatorId  操作人
     */
    public void softDeleteTaskStepsFromStepNo(Long taskId, Integer fromStepNo, Long operatorId) {
        String sql = """
                UPDATE task_steps
                SET is_deleted = TRUE,
                    updated_at = ?,
                    updated_by = ?
                WHERE task_id = ?
                  AND step_no >= ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, now, operatorId, taskId, fromStepNo);
    }

    /**
     * 查询会话任务列表。
     *
     * @param conversationId 会话 ID
     * @return 任务列表
     */
    public List<TaskRecord> findTasksByConversationId(Long conversationId) {
        String sql = """
                SELECT id,
                       task_code,
                       trace_id,
                       conversation_id,
                       message_id,
                       agent_id,
                       parent_task_id,
                       root_task_id,
                       trigger_task_step_id,
                       task_kind,
                       execution_type,
                       current_plan_version,
                       execution_chain_json,
                       prompt_id,
                       prompt_name,
                       status,
                       started_at,
                       finished_at,
                       error_message,
                       created_at
                FROM tasks
                WHERE conversation_id = ?
                  AND is_deleted = FALSE
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, TASK_ROW_MAPPER, conversationId);
    }

    /**
     * 更新任务追踪ID。
     *
     * @param taskId      任务ID
     * @param traceId     追踪ID
     * @param operatorId  操作人
     */
    public void updateTaskTraceId(Long taskId, String traceId, Long operatorId) {
        String sql = """
                UPDATE tasks
                SET trace_id = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, traceId, now, operatorId, taskId);
    }

    /**
     * 绑定异步运行编码，用于幂等恢复和队列排查。
     */
    public void bindTaskRunCode(Long taskId, String runCode, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE tasks
                SET run_code = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, runCode, operatorId, taskId);
    }

    /**
     * 按异步运行编码查询任务。
     */
    public Optional<TaskRecord> findTaskByRunCode(String runCode) {
        String sql = """
                SELECT id, task_code, trace_id, conversation_id, message_id, agent_id,
                       parent_task_id, root_task_id, trigger_task_step_id, task_kind,
                       execution_type, current_plan_version, execution_chain_json,
                       prompt_id, prompt_name, status, started_at, finished_at,
                       error_message, created_at
                FROM tasks
                WHERE run_code = ? AND is_deleted = FALSE
                LIMIT 1
                """;
        List<TaskRecord> records = jdbcTemplate.query(sql, TASK_ROW_MAPPER, runCode);
        return records.stream().findFirst();
    }

    public Optional<TaskRecord> findTaskById(Long taskId) {
        String sql = """
                SELECT id,
                       task_code,
                       trace_id,
                       conversation_id,
                       message_id,
                       agent_id,
                       parent_task_id,
                       root_task_id,
                       trigger_task_step_id,
                       task_kind,
                       execution_type,
                       current_plan_version,
                       execution_chain_json,
                       prompt_id,
                       prompt_name,
                       status,
                       started_at,
                       finished_at,
                       error_message,
                       created_at
                FROM tasks
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<TaskRecord> records = jdbcTemplate.query(sql, TASK_ROW_MAPPER, taskId);
        return records.stream().findFirst();
    }

    /**
     * 查询任务步骤列表。
     *
     * @param taskId 任务 ID
     * @return 步骤列表
     */
    public List<TaskStepRecord> findTaskStepsByTaskId(Long taskId) {
        String sql = """
                SELECT id,
                       task_id,
                       step_no,
                       step_type,
                       step_name,
                       status,
                       retry_count,
                       COALESCE(input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot,
                       error_message,
                       started_at,
                       finished_at,
                       created_at
                FROM task_steps
                WHERE task_id = ?
                  AND is_deleted = FALSE
                ORDER BY step_no ASC, id ASC
                """;
        return jdbcTemplate.query(sql, TASK_STEP_ROW_MAPPER, taskId);
    }

    public List<TaskTraceRecord> findTaskTracesByTaskId(Long taskId) {
        String sql = """
                SELECT id,
                       task_id,
                       trace_id,
                       task_step_id,
                       event_type,
                       span_name,
                       trace_status,
                       COALESCE(input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot,
                       error_message,
                       started_at,
                       finished_at,
                       duration_ms,
                       COALESCE(metadata::text, '{}') AS metadata,
                       created_at
                FROM task_traces
                WHERE task_id = ?
                  AND is_deleted = FALSE
                ORDER BY started_at ASC, id ASC
                """;
        return jdbcTemplate.query(sql, TASK_TRACE_ROW_MAPPER, taskId);
    }

    public long countTaskTraceAnalytics(Long userId,
                                        String traceId,
                                        String eventType,
                                        String traceStatus,
                                        Long agentId,
                                        Long conversationId,
                                        Long taskId,
                                        String keyword,
                                        LocalDateTime startedFrom,
                                        LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT COUNT(1)
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    public List<TaskTraceAnalyticsRecord> pageTaskTraceAnalytics(Long userId,
                                                                 String traceId,
                                                                 String eventType,
                                                                 String traceStatus,
                                                                 Long agentId,
                                                                 Long conversationId,
                                                                 Long taskId,
                                                                 String keyword,
                                                                 LocalDateTime startedFrom,
                                                                 LocalDateTime startedTo,
                                                                 int offset,
                                                                 int limit) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT tt.id,
                       c.id AS conversation_id,
                       c.conversation_code,
                       t.id AS task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       tt.trace_id,
                       tt.task_step_id,
                       ts.step_no,
                       ts.step_name,
                       tt.event_type,
                       tt.span_name,
                       tt.trace_status,
                       tt.duration_ms,
                       tt.error_message,
                       COALESCE(tt.input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(tt.output_snapshot::text, '{}') AS output_snapshot,
                       COALESCE(tt.metadata::text, '{}') AS metadata,
                       tt.started_at,
                       tt.finished_at,
                       tt.created_at
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY tt.started_at DESC, tt.id DESC LIMIT ? OFFSET ?",
                TASK_TRACE_ANALYTICS_ROW_MAPPER,
                params.toArray());
    }

    /**
     * 按 Trace 事件 ID 查询用户有权访问的完整排查记录。
     *
     * @param userId      当前用户 ID
     * @param taskTraceId Trace 事件 ID
     * @return Trace 排查记录
     */
    public Optional<TaskTraceAnalyticsRecord> findTaskTraceAnalyticsById(Long userId, Long taskTraceId) {
        String sql = """
                SELECT tt.id,
                       c.id AS conversation_id,
                       c.conversation_code,
                       t.id AS task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       tt.trace_id,
                       tt.task_step_id,
                       ts.step_no,
                       ts.step_name,
                       tt.event_type,
                       tt.span_name,
                       tt.trace_status,
                       tt.duration_ms,
                       tt.error_message,
                       COALESCE(tt.input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(tt.output_snapshot::text, '{}') AS output_snapshot,
                       COALESCE(tt.metadata::text, '{}') AS metadata,
                       tt.started_at,
                       tt.finished_at,
                       tt.created_at
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.id = ?
                  AND tt.is_deleted = FALSE
                  AND c.user_id = ?
                """;
        return jdbcTemplate.query(sql, TASK_TRACE_ANALYTICS_ROW_MAPPER, taskTraceId, userId).stream().findFirst();
    }

    public TaskTraceMetricsRecord summarizeTaskTraceMetrics(Long userId,
                                                            String traceId,
                                                            String eventType,
                                                            String traceStatus,
                                                            Long agentId,
                                                            Long conversationId,
                                                            Long taskId,
                                                            String keyword,
                                                            LocalDateTime startedFrom,
                                                            LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT COUNT(1) AS total_count,
                       COUNT(DISTINCT tt.trace_id) AS trace_count,
                       COUNT(1) FILTER (WHERE tt.trace_status = 'failed') AS failed_count,
                       CAST(AVG(tt.duration_ms) AS BIGINT) AS avg_duration_ms
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        List<TaskTraceMetricsRecord> records = jdbcTemplate.query(sqlAndParams.sql(), TASK_TRACE_METRICS_ROW_MAPPER, sqlAndParams.params().toArray());
        return records.isEmpty() ? new TaskTraceMetricsRecord(0L, 0L, 0L, 0L) : records.get(0);
    }

    public List<TaskTraceMetricBucketRecord> summarizeTaskTraceByAgent(Long userId,
                                                                       String traceId,
                                                                       String eventType,
                                                                       String traceStatus,
                                                                       Long agentId,
                                                                       Long conversationId,
                                                                       Long taskId,
                                                                       String keyword,
                                                                       LocalDateTime startedFrom,
                                                                       LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT CAST(t.agent_id AS varchar) AS bucket_key,
                       COALESCE(c.agent_name, CONCAT('Agent#', t.agent_id)) AS bucket_label,
                       COUNT(1) AS count_value
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        return jdbcTemplate.query(sqlAndParams.sql() + " GROUP BY t.agent_id, c.agent_name ORDER BY count_value DESC, bucket_label ASC",
                TASK_TRACE_METRIC_BUCKET_ROW_MAPPER,
                sqlAndParams.params().toArray());
    }

    public List<TaskTraceMetricBucketRecord> summarizeTaskTraceByStatus(Long userId,
                                                                        String traceId,
                                                                        String eventType,
                                                                        String traceStatus,
                                                                        Long agentId,
                                                                        Long conversationId,
                                                                        Long taskId,
                                                                        String keyword,
                                                                        LocalDateTime startedFrom,
                                                                        LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT tt.trace_status AS bucket_key,
                       tt.trace_status AS bucket_label,
                       COUNT(1) AS count_value
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        return jdbcTemplate.query(sqlAndParams.sql() + " GROUP BY tt.trace_status ORDER BY count_value DESC, bucket_label ASC",
                TASK_TRACE_METRIC_BUCKET_ROW_MAPPER,
                sqlAndParams.params().toArray());
    }

    public List<TaskTraceMetricBucketRecord> summarizeTaskTraceByEventType(Long userId,
                                                                           String traceId,
                                                                           String eventType,
                                                                           String traceStatus,
                                                                           Long agentId,
                                                                           Long conversationId,
                                                                           Long taskId,
                                                                           String keyword,
                                                                           LocalDateTime startedFrom,
                                                                           LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT tt.event_type AS bucket_key,
                       tt.event_type AS bucket_label,
                       COUNT(1) AS count_value
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        return jdbcTemplate.query(sqlAndParams.sql() + " GROUP BY tt.event_type ORDER BY count_value DESC, bucket_label ASC",
                TASK_TRACE_METRIC_BUCKET_ROW_MAPPER,
                sqlAndParams.params().toArray());
    }

    public List<TaskTraceAnalyticsRecord> findSlowTaskTraceSpans(Long userId,
                                                                 String traceId,
                                                                 String eventType,
                                                                 String traceStatus,
                                                                 Long agentId,
                                                                 Long conversationId,
                                                                 Long taskId,
                                                                 String keyword,
                                                                 LocalDateTime startedFrom,
                                                                 LocalDateTime startedTo,
                                                                 int limit) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT tt.id,
                       c.id AS conversation_id,
                       c.conversation_code,
                       t.id AS task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       tt.trace_id,
                       tt.task_step_id,
                       ts.step_no,
                       ts.step_name,
                       tt.event_type,
                       tt.span_name,
                       tt.trace_status,
                       tt.duration_ms,
                       tt.error_message,
                       COALESCE(tt.input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(tt.output_snapshot::text, '{}') AS output_snapshot,
                       COALESCE(tt.metadata::text, '{}') AS metadata,
                       tt.started_at,
                       tt.finished_at,
                       tt.created_at
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY tt.duration_ms DESC, tt.started_at DESC LIMIT ?",
                TASK_TRACE_ANALYTICS_ROW_MAPPER,
                params.toArray());
    }

    public List<TaskTraceAnalyticsRecord> findRecentToolRelatedTraces(String toolCode, int limit) {
        String sql = """
                SELECT tt.id,
                       c.id AS conversation_id,
                       c.conversation_code,
                       t.id AS task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       tt.trace_id,
                       tt.task_step_id,
                       ts.step_no,
                       ts.step_name,
                       tt.event_type,
                       tt.span_name,
                       tt.trace_status,
                       tt.duration_ms,
                       tt.error_message,
                       COALESCE(tt.input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(tt.output_snapshot::text, '{}') AS output_snapshot,
                       COALESCE(tt.metadata::text, '{}') AS metadata,
                       tt.started_at,
                       tt.finished_at,
                       tt.created_at
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND tt.event_type IN ('tool_invoke_prepare', 'tool_invoke')
                  AND COALESCE(tt.input_snapshot ->> 'toolCode', '') = ?
                ORDER BY CASE WHEN tt.trace_status = 'failed' THEN 0 ELSE 1 END,
                         tt.started_at DESC,
                         tt.id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, TASK_TRACE_ANALYTICS_ROW_MAPPER, toolCode, limit);
    }

    public long countCompactionTraceAnalytics(Long userId,
                                              String traceId,
                                              String eventType,
                                              String traceStatus,
                                              Long agentId,
                                              Long conversationId,
                                              Long taskId,
                                              String keyword,
                                              LocalDateTime startedFrom,
                                              LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT COUNT(1)
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                  AND jsonb_exists(tt.metadata, 'contextCompaction')
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        Long value = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return value == null ? 0L : value;
    }

    public List<TaskTraceAnalyticsRecord> pageCompactionTraceAnalytics(Long userId,
                                                                       String traceId,
                                                                       String eventType,
                                                                       String traceStatus,
                                                                       Long agentId,
                                                                       Long conversationId,
                                                                       Long taskId,
                                                                       String keyword,
                                                                       LocalDateTime startedFrom,
                                                                       LocalDateTime startedTo,
                                                                       int offset,
                                                                       int limit) {
        SqlAndParams sqlAndParams = buildTaskTraceAnalyticsSql("""
                SELECT tt.id,
                       c.id AS conversation_id,
                       c.conversation_code,
                       t.id AS task_id,
                       t.task_code,
                       t.agent_id,
                       c.agent_name,
                       tt.trace_id,
                       tt.task_step_id,
                       ts.step_no,
                       ts.step_name,
                       tt.event_type,
                       tt.span_name,
                       tt.trace_status,
                       tt.duration_ms,
                       tt.error_message,
                       COALESCE(tt.input_snapshot::text, '{}') AS input_snapshot,
                       COALESCE(tt.output_snapshot::text, '{}') AS output_snapshot,
                       COALESCE(tt.metadata::text, '{}') AS metadata,
                       tt.started_at,
                       tt.finished_at,
                       tt.created_at
                FROM task_traces tt
                INNER JOIN tasks t ON t.id = tt.task_id AND t.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN task_steps ts ON ts.id = tt.task_step_id AND ts.is_deleted = FALSE
                WHERE tt.is_deleted = FALSE
                  AND c.user_id = ?
                  AND jsonb_exists(tt.metadata, 'contextCompaction')
                """, userId, traceId, eventType, traceStatus, agentId, conversationId, taskId, keyword, startedFrom, startedTo);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY tt.started_at DESC, tt.id DESC LIMIT ? OFFSET ?",
                TASK_TRACE_ANALYTICS_ROW_MAPPER,
                params.toArray());
    }

    public Long findApprovalAverageWaitMs(Long userId,
                                          Long agentId,
                                          Long conversationId,
                                          Long taskId,
                                          LocalDateTime startedFrom,
                                          LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildApprovalAnalyticsSql(userId, agentId, conversationId, taskId, startedFrom, startedTo);
        Long value = jdbcTemplate.queryForObject("""
                SELECT CAST(AVG(EXTRACT(EPOCH FROM (a.approved_at - a.created_at)) * 1000) AS BIGINT)
                FROM approvals a
                INNER JOIN conversations c ON c.id = a.conversation_id AND c.is_deleted = FALSE
                INNER JOIN tasks t ON t.id = a.task_id AND t.is_deleted = FALSE
                WHERE a.is_deleted = FALSE
                  AND a.approved_at IS NOT NULL
                """ + sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return value == null ? 0L : value;
    }

    public Long findApprovalMaxWaitMs(Long userId,
                                      Long agentId,
                                      Long conversationId,
                                      Long taskId,
                                      LocalDateTime startedFrom,
                                      LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildApprovalAnalyticsSql(userId, agentId, conversationId, taskId, startedFrom, startedTo);
        Long value = jdbcTemplate.queryForObject("""
                SELECT CAST(MAX(EXTRACT(EPOCH FROM (a.approved_at - a.created_at)) * 1000) AS BIGINT)
                FROM approvals a
                INNER JOIN conversations c ON c.id = a.conversation_id AND c.is_deleted = FALSE
                INNER JOIN tasks t ON t.id = a.task_id AND t.is_deleted = FALSE
                WHERE a.is_deleted = FALSE
                  AND a.approved_at IS NOT NULL
                """ + sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return value == null ? 0L : value;
    }

    public Long insertGuardrailPolicyHit(Long conversationId,
                                         Long taskId,
                                         Long taskStepId,
                                         Long agentId,
                                         String traceId,
                                         String policyCode,
                                         String policyName,
                                         Integer policyVersion,
                                         String decisionType,
                                         String resourceKind,
                                         Long resourceId,
                                         String resourceCode,
                                         String resourceName,
                                         String hitDetail,
                                         String metadata,
                                         Long operatorId) {
        String sql = """
                INSERT INTO guardrail_policy_hits (
                    conversation_id,
                    task_id,
                    task_step_id,
                    agent_id,
                    trace_id,
                    policy_code,
                    policy_name,
                    policy_version,
                    decision_type,
                    resource_kind,
                    resource_id,
                    resource_code,
                    resource_name,
                    hit_detail,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, CAST(? AS BIGINT), ?, ?, ?, ?, ?, ?, ?, CAST(? AS BIGINT), ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(sql, Long.class,
                conversationId, taskId, taskStepId, agentId, traceId, policyCode, policyName, policyVersion,
                decisionType, resourceKind, resourceId, resourceCode, resourceName, hitDetail,
                metadata == null ? "{}" : metadata, now, operatorId, now, operatorId);
    }

    public long countGuardrailPolicyHits(Long userId,
                                         String policyCode,
                                         String decisionType,
                                         String traceId,
                                         Long taskId,
                                         Long agentId,
                                         LocalDateTime startedFrom,
                                         LocalDateTime startedTo) {
        SqlAndParams sqlAndParams = buildGuardrailHitQuerySql("""
                SELECT COUNT(1)
                FROM guardrail_policy_hits h
                INNER JOIN conversations c ON c.id = h.conversation_id AND c.is_deleted = FALSE
                WHERE h.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, policyCode, decisionType, traceId, taskId, agentId, startedFrom, startedTo);
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    public List<GuardrailPolicyHitRecord> pageGuardrailPolicyHits(Long userId,
                                                                  String policyCode,
                                                                  String decisionType,
                                                                  String traceId,
                                                                  Long taskId,
                                                                  Long agentId,
                                                                  LocalDateTime startedFrom,
                                                                  LocalDateTime startedTo,
                                                                  int offset,
                                                                  int limit) {
        SqlAndParams sqlAndParams = buildGuardrailHitQuerySql("""
                SELECT h.id,
                       h.conversation_id,
                       h.task_id,
                       h.task_step_id,
                       h.agent_id,
                       h.trace_id,
                       h.policy_code,
                       h.policy_name,
                       h.policy_version,
                       h.decision_type,
                       h.resource_kind,
                       h.resource_id,
                       h.resource_code,
                       h.resource_name,
                       h.hit_detail,
                       COALESCE(h.metadata::text, '{}') AS metadata,
                       h.created_at
                FROM guardrail_policy_hits h
                INNER JOIN conversations c ON c.id = h.conversation_id AND c.is_deleted = FALSE
                WHERE h.is_deleted = FALSE
                  AND c.user_id = ?
                """, userId, policyCode, decisionType, traceId, taskId, agentId, startedFrom, startedTo);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY h.created_at DESC, h.id DESC LIMIT ? OFFSET ?",
                GUARDRAIL_POLICY_HIT_ROW_MAPPER,
                params.toArray());
    }

    /**
     * 查询任务计划列表。
     *
     * @param taskId 任务 ID
     * @return 计划列表
     */
    public List<TaskPlanRecord> findTaskPlansByTaskId(Long taskId) {
        String sql = """
                SELECT id,
                       task_id,
                       plan_version,
                       plan_id,
                       plan_type,
                       goal,
                       status,
                       COALESCE(assumptions_json::text, '[]') AS assumptions_json,
                       COALESCE(constraints_json::text, '[]') AS constraints_json,
                       COALESCE(risks_json::text, '[]') AS risks_json,
                       planner_prompt_id,
                       planner_prompt_name,
                       source,
                       COALESCE(metadata::text, '{}') AS metadata,
                       created_at
                FROM task_plans
                WHERE task_id = ?
                  AND is_deleted = FALSE
                ORDER BY plan_version DESC, id DESC
                """;
        return jdbcTemplate.query(sql, TASK_PLAN_ROW_MAPPER, taskId);
    }

    public List<TaskPlanRecord> findRecentSuccessfulPlans(Long userId, Long agentId, int limit) {
        String sql = """
                SELECT tp.id,
                       tp.task_id,
                       tp.plan_version,
                       tp.plan_id,
                       tp.plan_type,
                       tp.goal,
                       tp.status,
                       COALESCE(tp.assumptions_json::text, '[]') AS assumptions_json,
                       COALESCE(tp.constraints_json::text, '[]') AS constraints_json,
                       COALESCE(tp.risks_json::text, '[]') AS risks_json,
                       tp.planner_prompt_id,
                       tp.planner_prompt_name,
                       tp.source,
                       COALESCE(tp.metadata::text, '{}') AS metadata,
                       tp.created_at
                FROM task_plans tp
                JOIN tasks t ON t.id = tp.task_id AND t.is_deleted = FALSE
                JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                WHERE tp.is_deleted = FALSE
                  AND c.user_id = ?
                  AND t.agent_id = ?
                  AND t.status = 'success'
                ORDER BY tp.created_at DESC, tp.id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, TASK_PLAN_ROW_MAPPER, userId, agentId, limit);
    }

    /**
     * 查询最近已结束任务的计划，用于把真实执行结果反哺 Planner。
     */
    public List<TaskPlanRecord> findRecentOutcomePlans(Long userId, Long agentId, int limit) {
        String sql = """
                SELECT tp.id,
                       tp.task_id,
                       tp.plan_version,
                       tp.plan_id,
                       tp.plan_type,
                       tp.goal,
                       tp.status,
                       COALESCE(tp.assumptions_json::text, '[]') AS assumptions_json,
                       COALESCE(tp.constraints_json::text, '[]') AS constraints_json,
                       COALESCE(tp.risks_json::text, '[]') AS risks_json,
                       tp.planner_prompt_id,
                       tp.planner_prompt_name,
                       tp.source,
                       COALESCE(tp.metadata::text, '{}') AS metadata,
                       tp.created_at
                FROM task_plans tp
                JOIN tasks t ON t.id = tp.task_id AND t.is_deleted = FALSE
                JOIN conversations c ON c.id = t.conversation_id AND c.is_deleted = FALSE
                WHERE tp.is_deleted = FALSE
                  AND c.user_id = ?
                  AND t.agent_id = ?
                  AND t.status IN ('success', 'failed', 'cancelled')
                ORDER BY tp.created_at DESC, tp.id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, TASK_PLAN_ROW_MAPPER, userId, agentId, limit);
    }

    public void updateTaskPlanMetadata(Long taskPlanId,
                                       String metadataJson,
                                       Long operatorId) {
        jdbcTemplate.update("""
                UPDATE task_plans
                SET metadata = CAST(? AS jsonb),
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, metadataJson, operatorId, taskPlanId);
    }

    private SqlAndParams buildGuardrailHitQuerySql(String baseSql,
                                                   Long userId,
                                                   String policyCode,
                                                   String decisionType,
                                                   String traceId,
                                                   Long taskId,
                                                   Long agentId,
                                                   LocalDateTime startedFrom,
                                                   LocalDateTime startedTo) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (policyCode != null && !policyCode.isBlank()) {
            sql.append(" AND h.policy_code ILIKE ?");
            params.add("%" + policyCode.trim() + "%");
        }
        if (decisionType != null && !decisionType.isBlank()) {
            sql.append(" AND h.decision_type = ?");
            params.add(decisionType.trim());
        }
        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND h.trace_id ILIKE ?");
            params.add("%" + traceId.trim() + "%");
        }
        if (taskId != null) {
            sql.append(" AND h.task_id = ?");
            params.add(taskId);
        }
        if (agentId != null) {
            sql.append(" AND h.agent_id = ?");
            params.add(agentId);
        }
        if (startedFrom != null) {
            sql.append(" AND h.created_at >= ?");
            params.add(Timestamp.valueOf(startedFrom));
        }
        if (startedTo != null) {
            sql.append(" AND h.created_at <= ?");
            params.add(Timestamp.valueOf(startedTo));
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private SqlAndParams buildTaskTraceAnalyticsSql(String baseSql,
                                                    Long userId,
                                                    String traceId,
                                                    String eventType,
                                                    String traceStatus,
                                                    Long agentId,
                                                    Long conversationId,
                                                    Long taskId,
                                                    String keyword,
                                                    LocalDateTime startedFrom,
                                                    LocalDateTime startedTo) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND tt.trace_id ILIKE ?");
            params.add("%" + traceId.trim() + "%");
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND tt.event_type = ?");
            params.add(eventType.trim());
        }
        if (traceStatus != null && !traceStatus.isBlank()) {
            sql.append(" AND tt.trace_status = ?");
            params.add(traceStatus.trim());
        }
        if (agentId != null) {
            sql.append(" AND t.agent_id = ?");
            params.add(agentId);
        }
        if (conversationId != null) {
            sql.append(" AND c.id = ?");
            params.add(conversationId);
        }
        if (taskId != null) {
            sql.append(" AND t.id = ?");
            params.add(taskId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (tt.span_name ILIKE ? OR COALESCE(tt.error_message, '') ILIKE ? OR c.conversation_code ILIKE ? OR t.task_code ILIKE ?)");
            String fuzzyKeyword = "%" + keyword.trim() + "%";
            params.add(fuzzyKeyword);
            params.add(fuzzyKeyword);
            params.add(fuzzyKeyword);
            params.add(fuzzyKeyword);
        }
        if (startedFrom != null) {
            sql.append(" AND tt.started_at >= ?");
            params.add(Timestamp.valueOf(startedFrom));
        }
        if (startedTo != null) {
            sql.append(" AND tt.started_at <= ?");
            params.add(Timestamp.valueOf(startedTo));
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private SqlAndParams buildApprovalAnalyticsSql(Long userId,
                                                   Long agentId,
                                                   Long conversationId,
                                                   Long taskId,
                                                   LocalDateTime startedFrom,
                                                   LocalDateTime startedTo) {
        StringBuilder sql = new StringBuilder("""
                  AND c.user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (agentId != null) {
            sql.append(" AND t.agent_id = ?");
            params.add(agentId);
        }
        if (conversationId != null) {
            sql.append(" AND c.id = ?");
            params.add(conversationId);
        }
        if (taskId != null) {
            sql.append(" AND t.id = ?");
            params.add(taskId);
        }
        if (startedFrom != null) {
            sql.append(" AND a.created_at >= ?");
            params.add(Timestamp.valueOf(startedFrom));
        }
        if (startedTo != null) {
            sql.append(" AND a.created_at <= ?");
            params.add(Timestamp.valueOf(startedTo));
        }
        return new SqlAndParams(sql.toString(), params);
    }

    /**
     * 查询任务计划步骤列表。
     *
     * @param taskPlanId 计划主键 ID
     * @return 计划步骤列表
     */
    public List<TaskPlanStepRecord> findTaskPlanStepsByTaskPlanId(Long taskPlanId) {
        String sql = """
                SELECT id,
                       task_plan_id,
                       task_id,
                       plan_version,
                       step_id,
                       step_no,
                       step_type,
                       step_name,
                       objective,
                       COALESCE(depends_on_json::text, '[]') AS depends_on_json,
                       target_tool_code,
                       target_workflow_id,
                       COALESCE(inputs_json::text, '{}') AS inputs_json,
                       expected_output,
                       failure_policy,
                       replannable,
                       status,
                       description,
                       created_at
                FROM task_plan_steps
                WHERE task_plan_id = ?
                  AND is_deleted = FALSE
                ORDER BY step_no ASC, id ASC
                """;
        return jdbcTemplate.query(sql, TASK_PLAN_STEP_ROW_MAPPER, taskPlanId);
    }

    private SqlAndParams buildConversationPageSql(String baseSql, Long userId, Long agentId) {
        StringBuilder sqlBuilder = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (agentId != null) {
            sqlBuilder.append(" AND agent_id = ?");
            params.add(agentId);
        }
        return new SqlAndParams(sqlBuilder.toString(), params);
    }

    private SqlAndParams buildTaskOperationsFilter(String baseSql,
                                                   Long userId,
                                                   String status,
                                                   Long agentId,
                                                   Long conversationId,
                                                   Long taskId,
                                                   String keyword) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND task.status = ?");
            params.add(status.trim());
        }
        if (agentId != null) {
            sql.append(" AND task.agent_id = ?");
            params.add(agentId);
        }
        if (conversationId != null) {
            sql.append(" AND task.conversation_id = ?");
            params.add(conversationId);
        }
        if (taskId != null) {
            sql.append(" AND task.id = ?");
            params.add(taskId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (task.task_code ILIKE ? OR conversation.conversation_code ILIKE ? OR COALESCE(conversation.title, '') ILIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private static Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static Integer getNullableInteger(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static JsonNode parseJson(String value) {
        try {
            if (value == null || value.isBlank()) {
                return OBJECT_MAPPER.createObjectNode();
            }
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private record SqlAndParams(String sql, List<Object> params) {
    }
}
