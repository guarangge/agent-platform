package com.zc.agent.core.modules.runtime.replay.repository;

import com.zc.agent.core.modules.runtime.replay.model.RuntimeReplayRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Runtime Replay 实例持久化仓库。 */
@Repository
public class RuntimeReplayRepository {

    private final JdbcTemplate jdbcTemplate;

    public RuntimeReplayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 创建 Replay，Manifest 和 Input 只允许在此时写入。 */
    public Long insert(ReplayInsertCommand command) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO runtime_replays (
                    replay_code, replay_trace_id, replay_mode, source_run_code,
                    source_task_id, source_task_trace_id, user_id, status,
                    manifest, input_snapshot, started_at, created_by, updated_by
                ) VALUES (
                    ?, ?, ?, (SELECT run_code FROM tasks WHERE id = ? AND is_deleted = FALSE),
                    ?, ?, ?, 'running', CAST(? AS jsonb), CAST(? AS jsonb),
                    CURRENT_TIMESTAMP, ?, ?
                ) RETURNING id
                """, Long.class,
                command.replayCode(), command.replayTraceId(), command.replayMode(), command.sourceTaskId(),
                command.sourceTaskId(), command.sourceTaskTraceId(), command.userId(),
                command.manifestJson(), command.inputSnapshotJson(), command.userId(), command.userId());
    }

    /** 只更新 Replay 结果，不允许覆盖 Manifest 或 Input。 */
    public int markSucceeded(String replayCode, String resultSnapshotJson, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE runtime_replays
                SET status = 'succeeded', result_snapshot = CAST(? AS jsonb), error_message = NULL,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE replay_code = ? AND status = 'running'
                """, resultSnapshotJson, operatorId, replayCode);
    }

    /** 标记 Replay 失败，保留原 Manifest 和 Input 用于排查。 */
    public int markFailed(String replayCode, String errorMessage, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE runtime_replays
                SET status = 'failed', error_message = ?, finished_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE replay_code = ? AND status = 'running'
                """, truncate(errorMessage), operatorId, replayCode);
    }

    /** 按管理筛选条件统计 Replay。 */
    public long count(String replayMode, String status, String sourceRunCode) {
        QueryParts query = filters(replayMode, status, sourceRunCode);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM runtime_replays" + query.whereClause(),
                Long.class,
                query.params().toArray()
        );
        return total == null ? 0L : total;
    }

    /** 按创建时间倒序分页查询 Replay。 */
    public List<RuntimeReplayRecord> page(String replayMode,
                                          String status,
                                          String sourceRunCode,
                                          int offset,
                                          int pageSize) {
        QueryParts query = filters(replayMode, status, sourceRunCode);
        List<Object> params = new ArrayList<>(query.params());
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.query("""
                        SELECT id, replay_code, replay_trace_id, replay_mode, source_run_code,
                               source_task_id, source_task_trace_id, user_id, status,
                               manifest::text AS manifest, input_snapshot::text AS input_snapshot,
                               result_snapshot::text AS result_snapshot,
                               error_message, started_at, finished_at, created_at
                        FROM runtime_replays
                        """ + query.whereClause() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapRecord,
                params.toArray());
    }

    /** 按稳定编码查询 Replay 详情。 */
    public Optional<RuntimeReplayRecord> findByReplayCode(String replayCode) {
        return jdbcTemplate.query("""
                        SELECT id, replay_code, replay_trace_id, replay_mode, source_run_code,
                               source_task_id, source_task_trace_id, user_id, status,
                               manifest::text AS manifest, input_snapshot::text AS input_snapshot,
                               result_snapshot::text AS result_snapshot,
                               error_message, started_at, finished_at, created_at
                        FROM runtime_replays
                        WHERE replay_code = ?
                        """,
                this::mapRecord,
                replayCode).stream().findFirst();
    }

    private QueryParts filters(String replayMode, String status, String sourceRunCode) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (replayMode != null) {
            conditions.add("replay_mode = ?");
            params.add(replayMode);
        }
        if (status != null) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (sourceRunCode != null) {
            conditions.add("source_run_code = ?");
            params.add(sourceRunCode);
        }
        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereClause, List.copyOf(params));
    }

    private RuntimeReplayRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new RuntimeReplayRecord(
                rs.getLong("id"), rs.getString("replay_code"), rs.getString("replay_trace_id"),
                rs.getString("replay_mode"), rs.getString("source_run_code"),
                nullableLong(rs, "source_task_id"), nullableLong(rs, "source_task_trace_id"),
                nullableLong(rs, "user_id"), rs.getString("status"), rs.getString("manifest"),
                rs.getString("input_snapshot"), rs.getString("result_snapshot"), rs.getString("error_message"),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) return value;
        return value.substring(0, 1000);
    }

    private record QueryParts(String whereClause, List<Object> params) {
    }

    /**
     * @param replayCode Replay 编码
     * @param replayTraceId Replay Trace ID
     * @param replayMode Replay 模式编码
     * @param sourceTaskId 原 Task ID
     * @param sourceTaskTraceId 原 Task Trace ID
     * @param userId 发起用户 ID
     * @param manifestJson 不可变 Manifest JSON
     * @param inputSnapshotJson 不可变 Input JSON
     */
    public record ReplayInsertCommand(
            String replayCode,
            String replayTraceId,
            String replayMode,
            Long sourceTaskId,
            Long sourceTaskTraceId,
            Long userId,
            String manifestJson,
            String inputSnapshotJson
    ) {
    }
}
