package com.zc.agent.core.modules.conversation.repository;

import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL 持久化 Agent 运行队列仓储。
 */
@Repository
public class AgentRunQueueRepository {

    private static final RowMapper<AgentRunRecord> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AgentRunRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AgentRunRecord(
                    rs.getLong("id"),
                    rs.getString("run_code"),
                    rs.getLong("conversation_id"),
                    rs.getLong("user_id"),
                    rs.getLong("agent_id"),
                    rs.getString("input_text"),
                    rs.getString("user_context"),
                    rs.getString("status"),
                    rs.getInt("attempt_count"),
                    rs.getInt("max_attempts"),
                    rs.getInt("priority"),
                    nullableLong(rs, "task_id"),
                    rs.getString("error_message"),
                    rs.getBoolean("cancel_requested"),
                    localDateTime(rs.getTimestamp("available_at")),
                    localDateTime(rs.getTimestamp("lease_until")),
                    rs.getString("worker_id"),
                    localDateTime(rs.getTimestamp("created_at")),
                    localDateTime(rs.getTimestamp("started_at")),
                    localDateTime(rs.getTimestamp("finished_at")),
                    rs.getString("worker_group_code"),
                    rs.getString("required_capabilities")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public AgentRunQueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Optional<Long> insert(String runCode,
                                 Long conversationId,
                                 Long userId,
                                 Long agentId,
                                 String inputText,
                                 String userContextJson,
                                 String idempotencyKey,
                                 int maxAttempts,
                                 int priority,
                                 int queueCapacity,
                                 int perUserQueueCapacity) {
        return insert(runCode,conversationId,userId,agentId,inputText,userContextJson,idempotencyKey,maxAttempts,priority,
                queueCapacity,perUserQueueCapacity,"default","[]");
    }

    @Transactional
    public Optional<Long> insert(String runCode,Long conversationId,Long userId,Long agentId,String inputText,
                                 String userContextJson,String idempotencyKey,int maxAttempts,int priority,
                                 int queueCapacity,int perUserQueueCapacity,String workerGroupCode,String requiredCapabilitiesJson) {
        acquireTransactionLock(731945821L);
        String sql = """
                WITH capacity AS (
                    SELECT
                        COUNT(*) FILTER (WHERE status IN ('queued', 'running') AND worker_group_code = ?) AS total_count,
                        COUNT(*) FILTER (WHERE status IN ('queued', 'running') AND user_id = ?) AS user_count
                    FROM agent_runs
                    WHERE is_deleted = FALSE
                )
                INSERT INTO agent_runs (
                    run_code, conversation_id, user_id, agent_id, input_text, user_context,
                    idempotency_key, status, max_attempts, priority, worker_group_code, required_capabilities,
                    available_at, created_at, updated_at
                )
                SELECT ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'queued', ?, ?, ?, CAST(? AS JSONB),
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM capacity
                WHERE total_count < ? AND user_count < ?
                RETURNING id
                """;
        List<Long> ids = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("id"),
                workerGroupCode,
                userId,
                runCode,
                conversationId,
                userId,
                agentId,
                inputText,
                userContextJson,
                idempotencyKey,
                maxAttempts,
                priority,
                workerGroupCode,
                requiredCapabilitiesJson,
                queueCapacity,
                perUserQueueCapacity
        );
        return ids.stream().findFirst();
    }

    public Optional<AgentRunRecord> findByRunCode(String runCode, Long userId) {
        return queryOne("""
                SELECT * FROM agent_runs
                WHERE run_code = ? AND user_id = ? AND is_deleted = FALSE
                LIMIT 1
                """, runCode, userId);
    }

    public Optional<AgentRunRecord> findByIdempotencyKey(Long conversationId, Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryOne("""
                SELECT * FROM agent_runs
                WHERE conversation_id = ? AND user_id = ? AND idempotency_key = ? AND is_deleted = FALSE
                LIMIT 1
                """, conversationId, userId, idempotencyKey);
    }

    /**
     * 使用事务 advisory lock 和 SKIP LOCKED 批量领取，确保多实例不突破全局并发容量。
     * 分组配额按用户和 Agent 分配，同组内再按优先级及等待 aging 排序。
     */
    @Transactional
    public List<AgentRunRecord> claimBatch(String workerId,
                                           int leaseSeconds,
                                           int batchSize,
                                           int globalConcurrency,
                                           int agingSeconds) {
        return claimBatch(workerId,leaseSeconds,batchSize,globalConcurrency,agingSeconds,"default","[]");
    }

    @Transactional
    public List<AgentRunRecord> claimBatch(String workerId,int leaseSeconds,int batchSize,int groupConcurrency,
                                           int agingSeconds,String workerGroupCode,String capabilitiesJson) {
        acquireTransactionLock(731945822L);
        String sql = """
                WITH capacity AS (
                    SELECT GREATEST(0, ? - COUNT(*))::INTEGER AS slots
                    FROM agent_runs
                    WHERE is_deleted = FALSE
                      AND worker_group_code = ?
                      AND status = 'running'
                      AND lease_until >= CURRENT_TIMESTAMP
                ), eligible_base AS (
                    SELECT id,
                           user_id,
                           agent_id,
                           priority + FLOOR(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - created_at)) / ?) AS effective_priority,
                           available_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY user_id, agent_id
                               ORDER BY priority DESC, available_at ASC, id ASC
                           ) AS group_rank
                    FROM agent_runs
                    WHERE is_deleted = FALSE
                      AND cancel_requested = FALSE
                      AND attempt_count < max_attempts
                      AND available_at <= CURRENT_TIMESTAMP
                      AND worker_group_code = ?
                      AND required_capabilities <@ CAST(? AS JSONB)
                      AND (status = 'queued' OR (status = 'running' AND lease_until < CURRENT_TIMESTAMP))
                ), group_stats AS (
                    SELECT COUNT(*) AS group_count
                    FROM (SELECT DISTINCT user_id, agent_id FROM eligible_base) groups
                ), eligible AS (
                    SELECT eligible_base.*, group_stats.group_count
                    FROM eligible_base CROSS JOIN group_stats
                ), candidate AS (
                    SELECT ar.id
                    FROM agent_runs ar
                    JOIN eligible e ON e.id = ar.id
                    CROSS JOIN capacity
                    WHERE e.group_rank <= GREATEST(1, CEIL(?::NUMERIC / GREATEST(e.group_count, 1)))
                    ORDER BY e.effective_priority DESC, e.available_at ASC, ar.id ASC
                    FOR UPDATE OF ar SKIP LOCKED
                    LIMIT (SELECT LEAST(?, slots) FROM capacity)
                )
                UPDATE agent_runs ar
                SET status = 'running',
                    attempt_count = attempt_count + 1,
                    worker_id = ?,
                    lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    heartbeat_at = CURRENT_TIMESTAMP,
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE ar.id = candidate.id
                RETURNING ar.*
                """;
        return jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                groupConcurrency,
                workerGroupCode,
                Math.max(1, agingSeconds),
                workerGroupCode,
                capabilitiesJson,
                batchSize,
                batchSize,
                workerId,
                leaseSeconds
        );
    }

    public void bindTask(String runCode, Long taskId) {
        jdbcTemplate.update(
                "UPDATE agent_runs SET task_id = ?, updated_at = CURRENT_TIMESTAMP WHERE run_code = ? AND is_deleted = FALSE",
                taskId,
                runCode
        );
    }

    public boolean markCompleted(String runCode, String workerId, Long taskId, String status) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = ?, task_id = ?, lease_until = NULL, heartbeat_at = CURRENT_TIMESTAMP,
                    finished_at = CURRENT_TIMESTAMP, error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND worker_id = ? AND status = 'running' AND is_deleted = FALSE
                """, status, taskId, runCode, workerId) == 1;
    }

    public boolean markFailed(String runCode, String workerId, Long taskId, String errorMessage) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = 'failed', task_id = COALESCE(?, task_id), lease_until = NULL,
                    heartbeat_at = CURRENT_TIMESTAMP, finished_at = CURRENT_TIMESTAMP,
                    error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND worker_id = ? AND status = 'running' AND is_deleted = FALSE
                """, taskId, truncate(errorMessage), runCode, workerId) == 1;
    }

    public boolean requeue(String runCode, String workerId, String errorMessage, int backoffSeconds) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = 'queued', worker_id = NULL, lease_until = NULL,
                    available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND worker_id = ? AND status = 'running'
                  AND is_deleted = FALSE AND cancel_requested = FALSE
                """, backoffSeconds, truncate(errorMessage), runCode, workerId) == 1;
    }

    public int requestCancel(String runCode, Long userId) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET cancel_requested = TRUE,
                    status = CASE WHEN status = 'queued' THEN 'cancelled' ELSE status END,
                    finished_at = CASE WHEN status = 'queued' THEN CURRENT_TIMESTAMP ELSE finished_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND user_id = ? AND status IN ('queued', 'running') AND is_deleted = FALSE
                """, runCode, userId);
    }

    public boolean isCancellationRequested(String runCode) {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT cancel_requested FROM agent_runs WHERE run_code = ? AND is_deleted = FALSE",
                Boolean.class,
                runCode
        );
        return Boolean.TRUE.equals(result);
    }

    public boolean markCancelled(String runCode, String workerId, Long taskId) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = 'cancelled', task_id = COALESCE(?, task_id), lease_until = NULL,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND worker_id = ? AND status = 'running' AND is_deleted = FALSE
                """, taskId, runCode, workerId) == 1;
    }

    public int extendWorkerLeases(String workerId, int leaseSeconds) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    heartbeat_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE worker_id = ? AND status = 'running' AND is_deleted = FALSE
                """, leaseSeconds, workerId);
    }

    /** 将已领取但未能提交到本地线程池的任务立即归还队列。 */
    public int releaseClaim(String runCode, String workerId) {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = 'queued', worker_id = NULL, lease_until = NULL,
                    heartbeat_at = NULL, attempt_count = GREATEST(0, attempt_count - 1),
                    available_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND worker_id = ? AND status = 'running' AND is_deleted = FALSE
                """, runCode, workerId);
    }

    /** 回收过期租约；已耗尽尝试次数的任务直接失败，其余任务重新排队。 */
    public int recoverExpiredLeases() {
        return jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = CASE WHEN attempt_count >= max_attempts THEN 'failed' ELSE 'queued' END,
                    worker_id = NULL, lease_until = NULL, heartbeat_at = NULL,
                    available_at = CURRENT_TIMESTAMP,
                    finished_at = CASE WHEN attempt_count >= max_attempts THEN CURRENT_TIMESTAMP ELSE NULL END,
                    error_message = CASE WHEN attempt_count >= max_attempts
                        THEN COALESCE(error_message, 'Worker 租约失效且已达到最大尝试次数')
                        ELSE error_message END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'running' AND lease_until < CURRENT_TIMESTAMP AND is_deleted = FALSE
                """);
    }

    public QueueMetrics loadMetrics() {
        Map<String, Long> statusCounts = jdbcTemplate.query("""
                        SELECT status, COUNT(*) AS count
                        FROM agent_runs WHERE is_deleted = FALSE GROUP BY status
                        """,
                rs -> {
                    Map<String, Long> result = new java.util.LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("status"), rs.getLong("count"));
                    }
                    return result;
                });
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) FILTER (WHERE status = 'queued' AND available_at <= CURRENT_TIMESTAMP) AS ready_count,
                    COUNT(*) FILTER (WHERE status = 'running' AND lease_until >= CURRENT_TIMESTAMP) AS leased_count,
                    COUNT(*) FILTER (WHERE status = 'running' AND lease_until < CURRENT_TIMESTAMP) AS expired_count,
                    MIN(created_at) FILTER (WHERE status = 'queued') AS oldest_queued_at
                FROM agent_runs WHERE is_deleted = FALSE
                """, (rs, rowNum) -> new QueueMetrics(
                statusCounts,
                rs.getLong("ready_count"),
                rs.getLong("leased_count"),
                rs.getLong("expired_count"),
                localDateTime(rs.getTimestamp("oldest_queued_at"))
        ));
    }

    /**
     * 将人工恢复后的 Task 状态同步回关联 Run，并返回实际发生的状态迁移。
     * 相同状态与错误重复提交时不更新，避免产生重复终态事件。
     */
    public List<RunStatusUpdate> syncTaskStatus(Long taskId, String taskStatus, String errorMessage) {
        String runStatus = switch (taskStatus == null ? "" : taskStatus) {
            case "success" -> "completed";
            case "waiting_approval" -> "waiting_approval";
            case "waiting_event" -> "waiting_event";
            case "paused" -> "paused";
            case "failed" -> "failed";
            default -> "running";
        };
        String normalizedError = truncate(errorMessage);
        return jdbcTemplate.query("""
                UPDATE agent_runs
                SET status = ?, error_message = ?,
                    finished_at = CASE WHEN ? IN ('completed', 'failed') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND is_deleted = FALSE
                  AND (status IS DISTINCT FROM ? OR error_message IS DISTINCT FROM ?)
                RETURNING run_code, task_id, status
                """, (rs, rowNum) -> new RunStatusUpdate(
                        rs.getString("run_code"), rs.getLong("task_id"), rs.getString("status")),
                runStatus, normalizedError, runStatus, taskId, runStatus, normalizedError);
    }

    /** 根据 Task ID 查询关联的持久化 Run 编码。 */
    public Optional<String> findRunCodeByTaskId(Long taskId) {
        List<String> runCodes = jdbcTemplate.queryForList("""
                SELECT run_code
                FROM agent_runs
                WHERE task_id = ? AND is_deleted = FALSE
                ORDER BY id DESC
                LIMIT 1
                """, String.class, taskId);
        return runCodes.stream().findFirst();
    }

    private Optional<AgentRunRecord> queryOne(String sql, Object... args) {
        List<AgentRunRecord> records = jdbcTemplate.query(sql, ROW_MAPPER, args);
        return records.stream().findFirst();
    }

    private void acquireTransactionLock(long lockId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> null, lockId);
    }

    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    /** 队列数据库指标快照。 */
    public record QueueMetrics(
            Map<String, Long> statusCounts,
            long readyCount,
            long leasedCount,
            long expiredLeaseCount,
            LocalDateTime oldestQueuedAt
    ) {
    }

    /** 人工恢复入口实际写入的 Run 状态。 */
    public record RunStatusUpdate(String runCode, Long taskId, String status) {
    }
}
