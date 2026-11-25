package com.zc.agent.core.modules.tool.repository;

import com.zc.agent.core.modules.tool.model.ToolHealthCheckMetricsRecord;
import com.zc.agent.core.modules.tool.model.ToolHealthCheckRecord;
import com.zc.agent.core.modules.tool.model.ToolHealthCheckTrendBucketRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tool 健康检查历史仓储。
 */
@Repository
public class ToolHealthCheckRepository {

    private static final RowMapper<ToolHealthCheckRecord> RECORD_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ToolHealthCheckRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ToolHealthCheckRecord(
                    rs.getLong("id"),
                    rs.getLong("tool_id"),
                    rs.getString("tool_code"),
                    rs.getString("tool_name"),
                    rs.getString("provider_type"),
                    rs.getString("transport_type"),
                    rs.getString("provider_code"),
                    rs.getString("provider_name"),
                    rs.getString("provider_instance_code"),
                    rs.getString("provider_instance_name"),
                    rs.getString("environment_code"),
                    rs.getString("endpoint"),
                    rs.getString("operation_name"),
                    rs.getString("operation_type"),
                    rs.getString("health_status"),
                    rs.getBoolean("success"),
                    rs.getString("message"),
                    rs.getString("failure_reason"),
                    rs.getObject("status_code", Integer.class),
                    rs.getObject("latency_ms", Long.class),
                    rs.getString("output_snapshot"),
                    rs.getObject("checked_at", LocalDateTime.class)
            );
        }
    };

    private static final RowMapper<ToolHealthCheckTrendBucketRecord> TREND_BUCKET_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ToolHealthCheckTrendBucketRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ToolHealthCheckTrendBucketRecord(
                    rs.getString("bucket_key"),
                    rs.getString("bucket_label"),
                    rs.getLong("total_count"),
                    rs.getLong("success_count"),
                    rs.getLong("failed_count")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public ToolHealthCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ToolHealthCheckRecord record, Long operatorId) {
        String sql = """
                INSERT INTO tool_health_checks (
                    tool_id, tool_code, tool_name, provider_type, transport_type, provider_code, provider_name,
                    provider_instance_code, provider_instance_name, environment_code, endpoint, operation_name,
                    operation_type, health_status, success, message, failure_reason, status_code, latency_ms,
                    output_snapshot, checked_at, created_at, created_by, updated_at, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp checkedAt = Timestamp.valueOf(record.checkedAt() == null ? LocalDateTime.now() : record.checkedAt());
        jdbcTemplate.update(
                sql,
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.providerType(),
                record.transportType(),
                record.providerCode(),
                record.providerName(),
                record.providerInstanceCode(),
                record.providerInstanceName(),
                record.environmentCode(),
                record.endpoint(),
                record.operationName(),
                record.operationType(),
                record.healthStatus(),
                record.success(),
                record.message(),
                record.failureReason(),
                record.statusCode(),
                record.latencyMs(),
                record.outputSnapshot() == null ? "{}" : record.outputSnapshot(),
                checkedAt,
                now,
                operatorId,
                now,
                operatorId
        );
    }

    public long countByToolId(Long toolId) {
        String sql = """
                SELECT COUNT(1)
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                """;
        Long result = jdbcTemplate.queryForObject(sql, Long.class, toolId);
        return result == null ? 0L : result;
    }

    public List<ToolHealthCheckRecord> pageByToolId(Long toolId, int offset, int limit) {
        String sql = """
                SELECT id, tool_id, tool_code, tool_name, provider_type, transport_type, provider_code, provider_name,
                       provider_instance_code, provider_instance_name, environment_code, endpoint, operation_name,
                       operation_type, health_status, success, message, failure_reason, status_code, latency_ms,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot, checked_at
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                ORDER BY checked_at DESC, id DESC
                OFFSET ?
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, RECORD_ROW_MAPPER, toolId, offset, limit);
    }

    public Optional<ToolHealthCheckRecord> findLatestByToolId(Long toolId) {
        String sql = """
                SELECT id, tool_id, tool_code, tool_name, provider_type, transport_type, provider_code, provider_name,
                       provider_instance_code, provider_instance_name, environment_code, endpoint, operation_name,
                       operation_type, health_status, success, message, failure_reason, status_code, latency_ms,
                       COALESCE(output_snapshot::text, '{}') AS output_snapshot, checked_at
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                ORDER BY checked_at DESC, id DESC
                LIMIT 1
                """;
        List<ToolHealthCheckRecord> records = jdbcTemplate.query(sql, RECORD_ROW_MAPPER, toolId);
        return records.stream().findFirst();
    }

    public Optional<String> findLatestFailureReasonByToolId(Long toolId) {
        String sql = """
                SELECT failure_reason
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                  AND success = FALSE
                  AND failure_reason IS NOT NULL
                  AND failure_reason <> ''
                ORDER BY checked_at DESC, id DESC
                LIMIT 1
                """;
        List<String> reasons = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("failure_reason"), toolId);
        return reasons.stream().findFirst();
    }

    public ToolHealthCheckMetricsRecord summarizeByToolId(Long toolId) {
        String sql = """
                SELECT COUNT(1) AS total_count,
                       COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_count,
                       COALESCE(SUM(CASE WHEN success THEN 0 ELSE 1 END), 0) AS failed_count,
                       COALESCE(AVG(latency_ms), 0) AS avg_latency_ms,
                       MAX(checked_at) AS last_checked_at,
                       MAX(CASE WHEN success THEN checked_at ELSE NULL END) AS last_success_at
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ToolHealthCheckMetricsRecord(
                rs.getLong("total_count"),
                rs.getLong("success_count"),
                rs.getLong("failed_count"),
                rs.getLong("avg_latency_ms"),
                rs.getObject("last_checked_at", LocalDateTime.class),
                rs.getObject("last_success_at", LocalDateTime.class)
        ), toolId);
    }

    public List<ToolHealthCheckTrendBucketRecord> summarizeTrendByToolId(Long toolId, int recentDays) {
        String sql = """
                SELECT TO_CHAR(DATE_TRUNC('day', checked_at), 'YYYY-MM-DD') AS bucket_key,
                       TO_CHAR(DATE_TRUNC('day', checked_at), 'MM-DD') AS bucket_label,
                       COUNT(1) AS total_count,
                       COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_count,
                       COALESCE(SUM(CASE WHEN success THEN 0 ELSE 1 END), 0) AS failed_count
                FROM tool_health_checks
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                  AND checked_at >= (CURRENT_TIMESTAMP - (? || ' days')::interval)
                GROUP BY DATE_TRUNC('day', checked_at)
                ORDER BY DATE_TRUNC('day', checked_at) ASC
                """;
        return jdbcTemplate.query(sql, TREND_BUCKET_ROW_MAPPER, toolId, recentDays);
    }
}
