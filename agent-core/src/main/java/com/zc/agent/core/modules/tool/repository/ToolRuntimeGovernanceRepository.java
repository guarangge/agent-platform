package com.zc.agent.core.modules.tool.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tool 实例熔断状态与运行路由审计仓储。 */
@Repository
public class ToolRuntimeGovernanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ToolRuntimeGovernanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RuntimeState> findState(Long deploymentId) {
        return jdbcTemplate.query("""
                SELECT deployment_id, circuit_state, consecutive_failures, next_probe_at
                FROM tool_instance_runtime_states WHERE deployment_id = ?
                """, (rs, rowNum) -> new RuntimeState(rs.getLong("deployment_id"), rs.getString("circuit_state"),
                rs.getInt("consecutive_failures"), toLocalDateTime(rs.getTimestamp("next_probe_at"))), deploymentId)
                .stream().findFirst();
    }

    public void recordSuccess(Long deploymentId, Long toolId, String deploymentCode) {
        jdbcTemplate.update("""
                INSERT INTO tool_instance_runtime_states(deployment_id, tool_id, deployment_code, circuit_state,
                    consecutive_failures, total_successes, last_success_at, updated_at)
                VALUES (?, ?, ?, 'closed', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (deployment_id) DO UPDATE SET circuit_state = 'closed', consecutive_failures = 0,
                    total_successes = tool_instance_runtime_states.total_successes + 1,
                    last_success_at = CURRENT_TIMESTAMP, last_error = NULL, opened_at = NULL,
                    next_probe_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, deploymentId, toolId, deploymentCode);
    }

    public int recordFailure(Long deploymentId, Long toolId, String deploymentCode, String error,
                             int threshold, int recoverySeconds) {
        jdbcTemplate.update("""
                INSERT INTO tool_instance_runtime_states(deployment_id, tool_id, deployment_code, circuit_state,
                    consecutive_failures, total_failures, last_failure_at, last_error, updated_at)
                VALUES (?, ?, ?, CASE WHEN ? <= 1 THEN 'open' ELSE 'closed' END, 1, 1,
                        CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (deployment_id) DO UPDATE SET
                    consecutive_failures = tool_instance_runtime_states.consecutive_failures + 1,
                    total_failures = tool_instance_runtime_states.total_failures + 1,
                    last_failure_at = CURRENT_TIMESTAMP, last_error = ?,
                    circuit_state = CASE WHEN tool_instance_runtime_states.consecutive_failures + 1 >= ?
                                         THEN 'open' ELSE tool_instance_runtime_states.circuit_state END,
                    opened_at = CASE WHEN tool_instance_runtime_states.consecutive_failures + 1 >= ?
                                     THEN CURRENT_TIMESTAMP ELSE tool_instance_runtime_states.opened_at END,
                    next_probe_at = CASE WHEN tool_instance_runtime_states.consecutive_failures + 1 >= ?
                                         THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                                         ELSE tool_instance_runtime_states.next_probe_at END,
                    updated_at = CURRENT_TIMESTAMP
                """, deploymentId, toolId, deploymentCode, threshold, error, error, threshold, threshold,
                threshold, recoverySeconds);
        return findState(deploymentId).map(RuntimeState::consecutiveFailures).orElse(1);
    }

    public void markHalfOpen(Long deploymentId) {
        jdbcTemplate.update("""
                UPDATE tool_instance_runtime_states SET circuit_state = 'half_open', updated_at = CURRENT_TIMESTAMP
                WHERE deployment_id = ? AND circuit_state = 'open' AND next_probe_at <= CURRENT_TIMESTAMP
                """, deploymentId);
    }

    public void insertAudit(Long toolId, Long deploymentId, String toolCode, String deploymentCode,
                            String action, String result, String traceId, String errorCode, String message,
                            String metadata) {
        jdbcTemplate.update("""
                INSERT INTO tool_runtime_route_audits(tool_id, deployment_id, tool_code, deployment_code,
                    action, result, trace_id, error_code, message, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, toolId, deploymentId, toolCode, deploymentCode, action, result, traceId, errorCode,
                message, metadata == null ? "{}" : metadata);
    }

    public List<Map<String, Object>> findStates(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT deployment_id, deployment_code, circuit_state, consecutive_failures, total_successes,
                       total_failures, last_success_at, last_failure_at, last_error, opened_at, next_probe_at, updated_at
                FROM tool_instance_runtime_states WHERE tool_id = ? ORDER BY deployment_code
                """, toolId);
    }

    public List<Map<String, Object>> findAudits(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT id, deployment_id, deployment_code, action, result, trace_id, error_code, message, created_at
                FROM tool_runtime_route_audits WHERE tool_id = ? ORDER BY created_at DESC LIMIT 200
                """, toolId);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record RuntimeState(Long deploymentId, String circuitState, int consecutiveFailures,
                               LocalDateTime nextProbeAt) {
    }
}
