package com.zc.agent.core.modules.runtime.capability.repository;

import com.zc.agent.core.modules.runtime.capability.dto.CapabilityPolicyAuditQueryRequest;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditBucketRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditMetricsRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditScope;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyCorrelatedEventRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 从统一 Runtime Event Store 查询 Capability Policy 决策事实。
 *
 * <p>审计数据以 {@code policy.decided} 事件为唯一事实源，避免再维护一套可能漂移的审计表。</p>
 */
@Repository
public class CapabilityPolicyAuditRepository {

    private static final String BASE_FROM = """
            FROM agent_run_stream_events e
            INNER JOIN agent_runs ar ON ar.run_code = e.run_code AND ar.is_deleted = FALSE
            INNER JOIN conversations c ON c.id = ar.conversation_id AND c.is_deleted = FALSE
            LEFT JOIN tasks t ON t.id = ar.task_id AND t.is_deleted = FALSE
            WHERE e.event_type = 'policy.decided'
              AND ar.user_id = ?
              AND ar.tenant_id = ?
              AND ar.organization_id = ?
              AND ar.workspace_id = ?
              AND ar.project_id = ?
            """;

    private static final RowMapper<CapabilityPolicyAuditRecord> AUDIT_ROW_MAPPER = (rs, rowNum) ->
            new CapabilityPolicyAuditRecord(
                    rs.getLong("event_id"),
                    rs.getLong("sequence_no"),
                    rs.getString("run_code"),
                    rs.getLong("conversation_id"),
                    rs.getString("conversation_code"),
                    nullableLong(rs, "task_id"),
                    rs.getString("task_code"),
                    rs.getLong("agent_id"),
                    rs.getString("agent_name"),
                    rs.getString("trace_id"),
                    rs.getString("tool_call_id"),
                    rs.getString("tool_code"),
                    rs.getString("tool_name"),
                    rs.getString("decision"),
                    rs.getString("requested_action"),
                    rs.getString("policy_code"),
                    nullableInteger(rs, "policy_version"),
                    rs.getString("runtime_capabilities"),
                    rs.getString("side_effect_level"),
                    rs.getString("parameter_hash"),
                    rs.getString("target_hash"),
                    rs.getString("approval_node_id"),
                    rs.getBoolean("approval_granted"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );

    private final JdbcTemplate jdbcTemplate;

    public CapabilityPolicyAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 统计符合当前用户和筛选条件的全部决策。 */
    public long count(CapabilityPolicyAuditScope scope, CapabilityPolicyAuditQueryRequest request) {
        SqlAndParams query = filteredSql("SELECT COUNT(1) " + BASE_FROM, scope, request);
        Long count = jdbcTemplate.queryForObject(query.sql(), Long.class, query.params().toArray());
        return count == null ? 0L : count;
    }

    /** 分页查询完整决策事实。 */
    public List<CapabilityPolicyAuditRecord> page(CapabilityPolicyAuditScope scope,
                                                   CapabilityPolicyAuditQueryRequest request,
                                                   int offset,
                                                   int limit) {
        SqlAndParams query = filteredSql(selectColumns() + BASE_FROM, scope, request);
        List<Object> params = new ArrayList<>(query.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(query.sql() + " ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?",
                AUDIT_ROW_MAPPER, params.toArray());
    }

    /** 查询决策总量和动作分布。 */
    public CapabilityPolicyAuditMetricsRecord metrics(CapabilityPolicyAuditScope scope,
                                                       CapabilityPolicyAuditQueryRequest request) {
        String select = """
                SELECT COUNT(1) AS total_count,
                       COUNT(1) FILTER (WHERE e.content::jsonb ->> 'decision' = 'allow') AS allow_count,
                       COUNT(1) FILTER (WHERE e.content::jsonb ->> 'decision' = 'sandbox') AS sandbox_count,
                       COUNT(1) FILTER (WHERE e.content::jsonb ->> 'decision' = 'approval') AS approval_count,
                       COUNT(1) FILTER (WHERE e.content::jsonb ->> 'decision' = 'block') AS block_count,
                       COUNT(1) FILTER (WHERE COALESCE((e.content::jsonb ->> 'approvalGranted')::boolean, FALSE)) AS approved_replay_count,
                       COUNT(DISTINCT e.run_code) AS run_count,
                       COUNT(DISTINCT (e.content::jsonb ->> 'toolCode')) AS tool_count
                """;
        SqlAndParams query = filteredSql(select + BASE_FROM, scope, request);
        return jdbcTemplate.query(query.sql(), (rs, rowNum) -> new CapabilityPolicyAuditMetricsRecord(
                rs.getLong("total_count"), rs.getLong("allow_count"), rs.getLong("sandbox_count"),
                rs.getLong("approval_count"), rs.getLong("block_count"), rs.getLong("approved_replay_count"),
                rs.getLong("run_count"), rs.getLong("tool_count")
        ), query.params().toArray()).stream().findFirst()
                .orElse(new CapabilityPolicyAuditMetricsRecord(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
    }

    /** 按决策动作聚合。 */
    public List<CapabilityPolicyAuditBucketRecord> decisionBuckets(CapabilityPolicyAuditScope scope,
                                                                    CapabilityPolicyAuditQueryRequest request) {
        SqlAndParams query = filteredSql("""
                SELECT e.content::jsonb ->> 'decision' AS bucket_key,
                       e.content::jsonb ->> 'decision' AS bucket_label,
                       COUNT(1) AS bucket_count
                """ + BASE_FROM, scope, request);
        return bucketQuery(query, " GROUP BY bucket_key, bucket_label ORDER BY bucket_count DESC, bucket_key ASC");
    }

    /** 按 Runtime Capability 聚合；一条多能力决策会分别计入各能力桶。 */
    public List<CapabilityPolicyAuditBucketRecord> capabilityBuckets(CapabilityPolicyAuditScope scope,
                                                                      CapabilityPolicyAuditQueryRequest request) {
        SqlAndParams query = filteredSql("""
                SELECT capability.value AS bucket_key,
                       capability.value AS bucket_label,
                       COUNT(1) AS bucket_count
                FROM agent_run_stream_events e
                INNER JOIN agent_runs ar ON ar.run_code = e.run_code AND ar.is_deleted = FALSE
                INNER JOIN conversations c ON c.id = ar.conversation_id AND c.is_deleted = FALSE
                LEFT JOIN tasks t ON t.id = ar.task_id AND t.is_deleted = FALSE
                CROSS JOIN LATERAL jsonb_array_elements_text(
                    COALESCE(e.content::jsonb -> 'runtimeCapabilities', '[]'::jsonb)
                ) AS capability(value)
                WHERE e.event_type = 'policy.decided'
                  AND ar.user_id = ?
                  AND ar.tenant_id = ?
                  AND ar.organization_id = ?
                  AND ar.workspace_id = ?
                  AND ar.project_id = ?
                """, scope, request);
        return bucketQuery(query, " GROUP BY capability.value ORDER BY bucket_count DESC, bucket_key ASC LIMIT 20");
    }

    /** 按 Tool 聚合。 */
    public List<CapabilityPolicyAuditBucketRecord> toolBuckets(CapabilityPolicyAuditScope scope,
                                                                CapabilityPolicyAuditQueryRequest request) {
        SqlAndParams query = filteredSql("""
                SELECT e.content::jsonb ->> 'toolCode' AS bucket_key,
                       COALESCE(NULLIF(e.content::jsonb ->> 'toolName', ''), e.content::jsonb ->> 'toolCode') AS bucket_label,
                       COUNT(1) AS bucket_count
                """ + BASE_FROM, scope, request);
        return bucketQuery(query, " GROUP BY bucket_key, bucket_label ORDER BY bucket_count DESC, bucket_key ASC LIMIT 20");
    }

    /** 按事件主键查询当前用户可见的决策。 */
    public Optional<CapabilityPolicyAuditRecord> findByEventId(CapabilityPolicyAuditScope scope, Long eventId) {
        String sql = selectColumns() + BASE_FROM + " AND e.id = ?";
        List<Object> params = new ArrayList<>(scopeParams(scope));
        params.add(eventId);
        return jdbcTemplate.query(sql, AUDIT_ROW_MAPPER, params.toArray()).stream().findFirst();
    }

    /** 查询同一 Run、同一 Tool Call 的策略、审批和 Tool 生命周期事件。 */
    public List<CapabilityPolicyCorrelatedEventRecord> findCorrelatedEvents(CapabilityPolicyAuditScope scope,
                                                                            String runCode,
                                                                            String toolCallId) {
        return jdbcTemplate.query("""
                SELECT e.id, e.sequence_no, e.event_type, e.content, e.metadata,
                       e.payload_version, e.created_at
                FROM agent_run_stream_events e
                INNER JOIN agent_runs ar ON ar.run_code = e.run_code AND ar.is_deleted = FALSE
                WHERE ar.user_id = ?
                  AND ar.tenant_id = ?
                  AND ar.organization_id = ?
                  AND ar.workspace_id = ?
                  AND ar.project_id = ?
                  AND e.run_code = ?
                  AND e.event_type IN (
                      'policy.decided', 'approval.requested', 'approval.resolved',
                      'tool.call.requested', 'tool.call.started', 'tool.call.completed', 'tool.call.failed'
                  )
                  AND e.content::jsonb ->> 'toolCallId' = ?
                ORDER BY e.sequence_no ASC
                """, (rs, rowNum) -> new CapabilityPolicyCorrelatedEventRecord(
                rs.getLong("id"), rs.getLong("sequence_no"), rs.getString("event_type"),
                rs.getString("content"), rs.getString("metadata"), rs.getInt("payload_version"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), scope.userId(), scope.tenantId(), scope.organizationId(), scope.workspaceId(), scope.projectId(),
                runCode, toolCallId);
    }

    private List<CapabilityPolicyAuditBucketRecord> bucketQuery(SqlAndParams query, String suffix) {
        return jdbcTemplate.query(query.sql() + suffix, (rs, rowNum) -> new CapabilityPolicyAuditBucketRecord(
                rs.getString("bucket_key"), rs.getString("bucket_label"), rs.getLong("bucket_count")
        ), query.params().toArray());
    }

    private SqlAndParams filteredSql(String baseSql,
                                     CapabilityPolicyAuditScope scope,
                                     CapabilityPolicyAuditQueryRequest request) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>(scopeParams(scope));
        appendEquals(sql, params, "e.content::jsonb ->> 'decision'", request.decision());
        appendLike(sql, params, "e.content::jsonb ->> 'toolCode'", request.toolCode());
        if (request.capability() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(")
                    .append("COALESCE(e.content::jsonb -> 'runtimeCapabilities', '[]'::jsonb)) capability(value) ")
                    .append("WHERE capability.value = ?)");
            params.add(request.capability());
        }
        appendEquals(sql, params, "e.content::jsonb ->> 'sideEffectLevel'", request.sideEffectLevel());
        appendLike(sql, params, "e.content::jsonb ->> 'policyCode'", request.policyCode());
        if (request.agentId() != null) {
            sql.append(" AND ar.agent_id = ?");
            params.add(request.agentId());
        }
        appendLike(sql, params, "e.run_code", request.runCode());
        appendLike(sql, params, "COALESCE(e.metadata ->> 'traceId', t.trace_id)", request.traceId());
        if (request.occurredFrom() != null) {
            sql.append(" AND e.created_at >= ?");
            params.add(Timestamp.valueOf(request.occurredFrom()));
        }
        if (request.occurredTo() != null) {
            sql.append(" AND e.created_at <= ?");
            params.add(Timestamp.valueOf(request.occurredTo()));
        }
        return new SqlAndParams(sql.toString(), List.copyOf(params));
    }

    private List<Object> scopeParams(CapabilityPolicyAuditScope scope) {
        return List.of(scope.userId(), scope.tenantId(), scope.organizationId(), scope.workspaceId(), scope.projectId());
    }

    private void appendEquals(StringBuilder sql, List<Object> params, String expression, String value) {
        if (value != null) {
            sql.append(" AND ").append(expression).append(" = ?");
            params.add(value);
        }
    }

    private void appendLike(StringBuilder sql, List<Object> params, String expression, String value) {
        if (value != null) {
            sql.append(" AND ").append(expression).append(" ILIKE ?");
            params.add("%" + value + "%");
        }
    }

    private String selectColumns() {
        return """
                SELECT e.id AS event_id,
                       e.sequence_no,
                       e.run_code,
                       c.id AS conversation_id,
                       c.conversation_code,
                       ar.task_id,
                       t.task_code,
                       ar.agent_id,
                       c.agent_name,
                       COALESCE(e.metadata ->> 'traceId', t.trace_id) AS trace_id,
                       e.content::jsonb ->> 'toolCallId' AS tool_call_id,
                       e.content::jsonb ->> 'toolCode' AS tool_code,
                       e.content::jsonb ->> 'toolName' AS tool_name,
                       e.content::jsonb ->> 'decision' AS decision,
                       e.content::jsonb ->> 'requestedAction' AS requested_action,
                       e.content::jsonb ->> 'policyCode' AS policy_code,
                       CAST(NULLIF(e.content::jsonb ->> 'policyVersion', '') AS INTEGER) AS policy_version,
                       COALESCE(e.content::jsonb -> 'runtimeCapabilities', '[]'::jsonb)::text AS runtime_capabilities,
                       e.content::jsonb ->> 'sideEffectLevel' AS side_effect_level,
                       e.content::jsonb ->> 'parameterHash' AS parameter_hash,
                       NULLIF(e.content::jsonb ->> 'targetHash', '') AS target_hash,
                       NULLIF(e.content::jsonb ->> 'approvalNodeId', '') AS approval_node_id,
                       COALESCE((e.content::jsonb ->> 'approvalGranted')::boolean, FALSE) AS approval_granted,
                       e.created_at
                """;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record SqlAndParams(String sql, List<Object> params) {
    }
}
