package com.zc.agent.registry.modules.tool.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tool 生命周期治理仓储。 */
@Repository
public class ToolLifecycleRepository {

    private final JdbcTemplate jdbcTemplate;

    public ToolLifecycleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean toolExists(Long toolId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM tools WHERE id = ? AND is_deleted = FALSE", Integer.class, toolId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> findCatalogs() {
        return jdbcTemplate.queryForList("""
                SELECT id, catalog_code, catalog_name, catalog_type, source_url, transport_type,
                       config::text AS config, status, last_discovered_at, last_error, created_at, updated_at
                FROM tool_catalogs WHERE is_deleted = FALSE ORDER BY id DESC
                """);
    }

    public Long insertCatalog(String code, String name, String type, String sourceUrl, String transport,
                              String config, String status, Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO tool_catalogs(catalog_code, catalog_name, catalog_type, source_url, transport_type,
                                          config, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """, code, name, type, sourceUrl, transport, config, status, operatorId, operatorId);
    }

    public void replaceCatalogItems(Long catalogId, List<DiscoveredItem> items) {
        jdbcTemplate.update("UPDATE tool_catalog_items SET is_deleted = TRUE WHERE catalog_id = ? AND is_deleted = FALSE", catalogId);
        for (DiscoveredItem item : items) {
            jdbcTemplate.update("""
                    INSERT INTO tool_catalog_items(catalog_id, remote_name, display_name, description, input_schema,
                                                   remote_version, metadata)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb)
                    """, catalogId, item.remoteName(), item.displayName(), item.description(), item.inputSchema(),
                    item.remoteVersion(), item.metadata());
        }
        jdbcTemplate.update("""
                UPDATE tool_catalogs SET last_discovered_at = CURRENT_TIMESTAMP, last_error = NULL,
                                         updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, catalogId);
    }

    public void markCatalogDiscoveryFailed(Long catalogId, String error) {
        if (catalogId != null) {
            jdbcTemplate.update("""
                    UPDATE tool_catalogs SET last_discovered_at = CURRENT_TIMESTAMP, last_error = ?,
                                             updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, error, catalogId);
        }
    }

    public List<Map<String, Object>> findCatalogItems(Long catalogId) {
        return jdbcTemplate.queryForList("""
                SELECT id, catalog_id, remote_name, display_name, description, input_schema::text AS input_schema,
                       remote_version, metadata::text AS metadata, discovered_at
                FROM tool_catalog_items WHERE catalog_id = ? AND is_deleted = FALSE ORDER BY remote_name
                """, catalogId);
    }

    public Optional<Map<String, Object>> findCatalogItem(Long catalogItemId) {
        return jdbcTemplate.queryForList("""
                SELECT id, catalog_id, remote_name, display_name, description, input_schema::text AS input_schema,
                       remote_version, metadata::text AS metadata
                FROM tool_catalog_items WHERE id = ? AND is_deleted = FALSE
                """, catalogItemId).stream().findFirst();
    }

    public Long insertInstalledTool(String code, String name, String type, String category, String description,
                                    String inputSchema, String permissionLevel, String runtimeCapabilities,
                                    String sideEffectLevel, String metadata, Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO tools(tool_code, tool_name, tool_type, tool_category, description, input_schema,
                                  output_schema, permission_level, runtime_capabilities, side_effect_level,
                                  timeout_seconds, status, metadata,
                                  created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb, ?, ?::jsonb, ?, 30, 'enabled', ?::jsonb, ?, ?)
                """, code, name, type, category, description, inputSchema, permissionLevel,
                runtimeCapabilities, sideEffectLevel, metadata,
                operatorId, operatorId);
    }

    public Long insertVersion(Long toolId, String version, String inputSchema, String outputSchema,
                              String invokeConfig, String releaseNotes, String compatibility, String status,
                              Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO tool_versions(tool_id, version, input_schema, output_schema, invoke_config,
                                          release_notes, compatibility, status, published_at, created_by, updated_by)
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?,
                        CASE WHEN ? = 'published' THEN CURRENT_TIMESTAMP ELSE NULL END, ?, ?)
                """, toolId, version, inputSchema, outputSchema, invokeConfig, releaseNotes, compatibility,
                status, status, operatorId, operatorId);
    }

    public List<Map<String, Object>> findVersions(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT id, tool_id, version, input_schema::text AS input_schema, output_schema::text AS output_schema,
                       invoke_config::text AS invoke_config, release_notes, compatibility, status, published_at,
                       created_at, updated_at
                FROM tool_versions WHERE tool_id = ? AND is_deleted = FALSE ORDER BY created_at DESC
                """, toolId);
    }

    public Optional<Map<String, Object>> findVersion(Long toolId, Long versionId) {
        return jdbcTemplate.queryForList("""
                SELECT id, tool_id, version, input_schema::text AS input_schema, output_schema::text AS output_schema,
                       invoke_config::text AS invoke_config, status
                FROM tool_versions WHERE id = ? AND tool_id = ? AND is_deleted = FALSE
                """, versionId, toolId).stream().findFirst();
    }

    public Long insertDeployment(Long toolId, Long versionId, String code, String name, String environment,
                                 String providerType, String transportType, String endpoint, String authType,
                                 String authConfig, String headers, String invokeConfig, int weight, int rollout,
                                 int priority, int timeout, int retry, int threshold, int recovery, String status,
                                 String metadata, Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO tool_deployments(tool_id, version_id, deployment_code, deployment_name,
                    environment_code, provider_type, transport_type, endpoint, auth_type, auth_config, headers,
                    invoke_config, traffic_weight, rollout_percent, priority, timeout_seconds, retry_times,
                    failure_threshold, recovery_seconds, status, drain_started_at, metadata, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?,
                        CASE WHEN ? = 'draining' THEN CURRENT_TIMESTAMP ELSE NULL END, ?::jsonb, ?, ?)
                """, toolId, versionId, code, name, environment, providerType, transportType, endpoint, authType,
                authConfig, headers, invokeConfig, weight, rollout, priority, timeout, retry, threshold, recovery,
                status, status, metadata, operatorId, operatorId);
    }

    public List<Map<String, Object>> findDeployments(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT td.id, td.tool_id, td.version_id, tv.version, td.deployment_code, td.deployment_name,
                       td.environment_code, td.provider_type, td.transport_type, td.endpoint, td.auth_type,
                       td.auth_config::text AS auth_config, td.headers::text AS headers,
                       COALESCE(td.invoke_config, tv.invoke_config)::text AS invoke_config,
                       tv.input_schema::text AS version_input_schema,
                       tv.output_schema::text AS version_output_schema,
                       td.traffic_weight, td.rollout_percent, td.priority,
                       td.timeout_seconds, td.retry_times, td.failure_threshold, td.recovery_seconds, td.status,
                       td.drain_started_at, td.metadata::text AS metadata, td.created_at, td.updated_at
                FROM tool_deployments td
                JOIN tool_versions tv ON tv.id = td.version_id AND tv.is_deleted = FALSE
                WHERE td.tool_id = ? AND td.is_deleted = FALSE
                ORDER BY td.priority, td.id
                """, toolId);
    }

    public Optional<Map<String, Object>> findDeployment(Long toolId, Long deploymentId) {
        return findDeployments(toolId).stream().filter(item -> deploymentId.equals(((Number) item.get("id")).longValue())).findFirst();
    }

    public int updateRoute(Long toolId, Long deploymentId, int weight, int rollout, int priority,
                           int threshold, int recovery, String status, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE tool_deployments SET traffic_weight = ?, rollout_percent = ?, priority = ?,
                    failure_threshold = ?, recovery_seconds = ?, status = ?,
                    drain_started_at = CASE WHEN ? = 'draining' AND status <> 'draining' THEN CURRENT_TIMESTAMP
                                            WHEN ? <> 'draining' THEN NULL ELSE drain_started_at END,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND tool_id = ? AND is_deleted = FALSE
                """, weight, rollout, priority, threshold, recovery, status, status, status, operatorId,
                deploymentId, toolId);
    }

    public Long insertMigration(Long toolId, Long sourceVersionId, Long targetVersionId, String strategy,
                                int rollout, String status, String validationResult, Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO tool_migrations(tool_id, source_version_id, target_version_id, strategy,
                    rollout_percent, status, validation_result, started_at, completed_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP,
                        CASE WHEN ? = 'completed' THEN CURRENT_TIMESTAMP ELSE NULL END, ?, ?)
                """, toolId, sourceVersionId, targetVersionId, strategy, rollout, status, validationResult,
                status, operatorId, operatorId);
    }

    public List<Map<String, Object>> findMigrations(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT tm.id, tm.tool_id, tm.source_version_id, sv.version AS source_version,
                       tm.target_version_id, tv.version AS target_version, tm.strategy, tm.rollout_percent,
                       tm.status, tm.validation_result::text AS validation_result, tm.error_message,
                       tm.started_at, tm.completed_at, tm.created_at
                FROM tool_migrations tm
                LEFT JOIN tool_versions sv ON sv.id = tm.source_version_id
                JOIN tool_versions tv ON tv.id = tm.target_version_id
                WHERE tm.tool_id = ? ORDER BY tm.created_at DESC
                """, toolId);
    }

    public Map<String, Object> dependencyImpact(Long toolId) {
        return jdbcTemplate.queryForMap("""
                WITH target_tool AS (SELECT tool_code FROM tools WHERE id = ?)
                SELECT
                    (SELECT COUNT(1) FROM agent_tool_rel WHERE tool_id = ? AND is_deleted = FALSE) AS agent_count,
                    (SELECT COUNT(1) FROM workflows w WHERE w.is_deleted = FALSE
                        AND (w.definition_json::text LIKE ? OR w.definition_json::text LIKE ?
                             OR w.definition_json::text LIKE ('%\"toolCode\": \"' || (SELECT tool_code FROM target_tool) || '\"%')
                             OR w.definition_json::text LIKE ('%\"toolCode\":\"' || (SELECT tool_code FROM target_tool) || '\"%'))) AS workflow_count,
                    (SELECT COUNT(1) FROM tool_deployments WHERE tool_id = ? AND is_deleted = FALSE
                        AND status IN ('enabled', 'draining')) AS active_deployment_count
                """, toolId, toolId, "%\"toolId\": " + toolId + "%", "%\"toolId\":" + toolId + "%", toolId);
    }

    public void retireTool(Long toolId, Long operatorId) {
        jdbcTemplate.update("UPDATE tool_deployments SET status = 'disabled', updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tool_id = ? AND is_deleted = FALSE", operatorId, toolId);
        jdbcTemplate.update("UPDATE tool_versions SET status = 'retired', updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tool_id = ? AND is_deleted = FALSE", operatorId, toolId);
        jdbcTemplate.update("UPDATE tools SET status = 'disabled', updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND is_deleted = FALSE", operatorId, toolId);
    }

    public List<Map<String, Object>> findGlobalAudits() {
        return jdbcTemplate.queryForList("""
                SELECT id, tool_id, deployment_id, action, before_state::text AS before_state,
                       after_state::text AS after_state, result, message, operator_id, created_at
                FROM tool_lifecycle_audits ORDER BY created_at DESC LIMIT 200
                """);
    }

    public void insertAudit(Long toolId, Long deploymentId, String action, String beforeState,
                            String afterState, String result, String message, Long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO tool_lifecycle_audits(tool_id, deployment_id, action, before_state, after_state,
                                                  result, message, operator_id)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """, toolId, deploymentId, action, beforeState, afterState, result, message, operatorId);
    }

    public List<Map<String, Object>> findAudits(Long toolId) {
        return jdbcTemplate.queryForList("""
                SELECT id, tool_id, deployment_id, action, before_state::text AS before_state,
                       after_state::text AS after_state, result, message, operator_id, created_at
                FROM tool_lifecycle_audits WHERE tool_id = ? ORDER BY created_at DESC LIMIT 200
                """, toolId);
    }

    private Long insertAndReturnKey(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public record DiscoveredItem(String remoteName, String displayName, String description,
                                 String inputSchema, String remoteVersion, String metadata) {
    }
}
