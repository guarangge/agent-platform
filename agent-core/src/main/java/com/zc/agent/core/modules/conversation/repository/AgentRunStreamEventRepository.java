package com.zc.agent.core.modules.conversation.repository;

import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/** 持久化 Run 原生流事件，供跨实例 SSE 消费和断线续传。 */
@Repository
public class AgentRunStreamEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRunStreamEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 原子分配 Run 内序号并追加事件。 */
    @Transactional
    public long append(String runCode, String eventType, String content, String metadataJson) {
        return append(runCode, eventType, content, metadataJson, 1);
    }

    /** 原子分配 Run 内序号并追加版本化事件。 */
    @Transactional
    public long append(String runCode,
                       String eventType,
                       String content,
                       String metadataJson,
                       int payloadVersion) {
        Long sequenceNo = jdbcTemplate.queryForObject("""
                UPDATE agent_runs
                SET stream_event_sequence = stream_event_sequence + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE run_code = ? AND is_deleted = FALSE
                RETURNING stream_event_sequence
                """, Long.class, runCode);
        if (sequenceNo == null) {
            throw new IllegalStateException("Agent Run 不存在，无法追加流事件: " + runCode);
        }
        jdbcTemplate.update("""
                INSERT INTO agent_run_stream_events (
                    run_code, sequence_no, event_type, content, metadata, payload_version, created_at
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP)
                """, runCode, sequenceNo, eventType, content, metadataJson == null ? "{}" : metadataJson,
                Math.max(1, payloadVersion));
        return sequenceNo;
    }

    /** 按 Run 内序号顺序读取尚未消费的事件。 */
    public List<AgentRunStreamEventRecord> findAfter(String runCode, long sequenceNo, int limit) {
        return jdbcTemplate.query("""
                SELECT id, run_code, sequence_no, event_type, content, metadata, payload_version, created_at
                FROM agent_run_stream_events
                WHERE run_code = ? AND sequence_no > ?
                ORDER BY sequence_no ASC
                LIMIT ?
                """, (rs, rowNum) -> new AgentRunStreamEventRecord(
                rs.getLong("id"),
                rs.getString("run_code"),
                rs.getLong("sequence_no"),
                rs.getString("event_type"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getInt("payload_version"),
                toLocalDateTime(rs.getTimestamp("created_at"))
        ), runCode, sequenceNo, Math.max(1, Math.min(limit, 500)));
    }

    /** 返回请求时已持久化的最后事件序号，供 Replay 冻结上界。 */
    public long findLastSequence(String runCode) {
        Long sequenceNo = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0)
                FROM agent_run_stream_events
                WHERE run_code = ?
                """, Long.class, runCode);
        return sequenceNo == null ? 0L : sequenceNo;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
