package com.zc.agent.registry.modules.runtimeplugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runtime Plugin 定义、版本、安装、Agent 绑定和审计仓储。 */
@Repository
public class RuntimePluginRepository {

    private final JdbcTemplate jdbcTemplate;

    public RuntimePluginRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查询当前项目作用域内的插件定义。 */
    public List<Map<String, Object>> findPlugins() {
        return jdbcTemplate.queryForList("""
                SELECT rp.id, rp.plugin_code, rp.plugin_name, rp.description, rp.status,
                       rp.metadata::text AS metadata, rp.created_at, rp.updated_at,
                       (SELECT COUNT(1) FROM runtime_plugin_versions rpv
                        WHERE rpv.plugin_id = rp.id AND rpv.is_deleted = FALSE) AS version_count,
                       (SELECT COUNT(1) FROM runtime_plugin_installations rpi
                        WHERE rpi.plugin_id = rp.id AND rpi.is_deleted = FALSE
                          AND rpi.status <> 'uninstalled') AS installation_count
                FROM runtime_plugins rp
                WHERE rp.is_deleted = FALSE
                ORDER BY rp.id DESC
                """);
    }

    /** 按 ID 查询插件定义。 */
    public Optional<Map<String, Object>> findPlugin(Long pluginId) {
        return jdbcTemplate.queryForList("""
                SELECT id, plugin_code, plugin_name, description, status,
                       metadata::text AS metadata, created_at, updated_at, created_by, updated_by
                FROM runtime_plugins
                WHERE id = ? AND is_deleted = FALSE
                """, pluginId).stream().findFirst();
    }

    /** 判断插件编码是否已在当前项目定义。 */
    public boolean pluginExistsByCode(String pluginCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM runtime_plugins
                WHERE plugin_code = ? AND is_deleted = FALSE
                """, Integer.class, pluginCode);
        return count != null && count > 0;
    }

    /** 新增插件定义并返回主键。 */
    public Long insertPlugin(String pluginCode,
                             String pluginName,
                             String description,
                             String status,
                             String metadata,
                             Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO runtime_plugins(plugin_code, plugin_name, description, status, metadata,
                                            created_by, updated_by)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                """, pluginCode, pluginName, description, status, metadata, operatorId, operatorId);
    }

    /** 更新插件定义启停状态。 */
    public int updatePluginStatus(Long pluginId, String status, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE runtime_plugins
                SET status = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, status, operatorId, pluginId);
    }

    /** 统计插件仍处于启用状态的环境安装。 */
    public int countEnabledInstallations(Long pluginId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM runtime_plugin_installations
                WHERE plugin_id = ? AND is_deleted = FALSE AND status = 'enabled'
                """, Integer.class, pluginId);
        return count == null ? 0 : count;
    }

    /** 新增不可变插件版本并返回主键。 */
    public Long insertVersion(Long pluginId,
                              String version,
                              String platformApiVersion,
                              String entrypoint,
                              String manifest,
                              String artifactUri,
                              String artifactSha256,
                              String releaseNotes,
                              String status,
                              Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO runtime_plugin_versions(plugin_id, version, platform_api_version, entrypoint,
                    manifest, artifact_uri, artifact_sha256, release_notes, status, published_at,
                    created_by, updated_by)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?,
                        CASE WHEN ? = 'published' THEN CURRENT_TIMESTAMP ELSE NULL END, ?, ?)
                """, pluginId, version, platformApiVersion, entrypoint, manifest, artifactUri,
                artifactSha256, releaseNotes, status, status, operatorId, operatorId);
    }

    /** 查询插件的指定版本。 */
    public Optional<Map<String, Object>> findVersion(Long pluginId, Long versionId) {
        return jdbcTemplate.queryForList("""
                SELECT id, plugin_id, version, platform_api_version, entrypoint,
                       manifest::text AS manifest, artifact_uri, artifact_sha256,
                       release_notes, status, published_at, created_at, updated_at
                FROM runtime_plugin_versions
                WHERE id = ? AND plugin_id = ? AND is_deleted = FALSE
                """, versionId, pluginId).stream().findFirst();
    }

    /** 查询插件全部版本。 */
    public List<Map<String, Object>> findVersions(Long pluginId) {
        return jdbcTemplate.queryForList("""
                SELECT id, plugin_id, version, platform_api_version, entrypoint,
                       manifest::text AS manifest, artifact_uri, artifact_sha256,
                       release_notes, status, published_at, created_at, updated_at
                FROM runtime_plugin_versions
                WHERE plugin_id = ? AND is_deleted = FALSE
                ORDER BY created_at DESC, id DESC
                """, pluginId);
    }

    /** 只更新版本生命周期状态，Manifest 和制品定位保持不可变。 */
    public int updateVersionStatus(Long pluginId, Long versionId, String status, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE runtime_plugin_versions
                SET status = ?,
                    published_at = CASE WHEN ? = 'published' AND published_at IS NULL
                                        THEN CURRENT_TIMESTAMP ELSE published_at END,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND plugin_id = ? AND is_deleted = FALSE
                """, status, status, operatorId, versionId, pluginId);
    }

    /** 统计仍使用指定版本的有效安装。 */
    public int countActiveInstallations(Long versionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM runtime_plugin_installations
                WHERE version_id = ? AND is_deleted = FALSE AND status = 'enabled'
                """, Integer.class, versionId);
        return count == null ? 0 : count;
    }

    /** 查询同一插件和环境的当前安装。 */
    public Optional<Map<String, Object>> findInstallationByEnvironment(Long pluginId, String environmentCode) {
        return jdbcTemplate.queryForList("""
                SELECT id, plugin_id, version_id, environment_code, status, load_status,
                       observed_plugin_code, observed_version, observed_artifact_sha256,
                       last_error, metadata::text AS metadata, installed_at, enabled_at,
                       disabled_at, uninstalled_at, loaded_at, last_reported_at, updated_at
                FROM runtime_plugin_installations
                WHERE plugin_id = ? AND environment_code = ? AND is_deleted = FALSE
                """, pluginId, environmentCode).stream().findFirst();
    }

    /** 新建或替换同一环境的安装版本；变更在重启后才进入已加载状态。 */
    public Long upsertInstallation(Long pluginId,
                                   Long versionId,
                                   String environmentCode,
                                   String status,
                                   String metadata,
                                   Long operatorId) {
        return insertAndReturnKey("""
                INSERT INTO runtime_plugin_installations(plugin_id, version_id, environment_code,
                    status, load_status, metadata, installed_at, enabled_at, disabled_at,
                    uninstalled_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'pending_restart', ?::jsonb, CURRENT_TIMESTAMP,
                        CASE WHEN ? = 'enabled' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        CASE WHEN ? = 'disabled' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        NULL, ?, ?)
                ON CONFLICT (plugin_id, environment_code) WHERE is_deleted = FALSE
                DO UPDATE SET version_id = EXCLUDED.version_id, status = EXCLUDED.status,
                    load_status = 'pending_restart', last_error = NULL, metadata = EXCLUDED.metadata,
                    installed_at = CURRENT_TIMESTAMP,
                    enabled_at = CASE WHEN EXCLUDED.status = 'enabled' THEN CURRENT_TIMESTAMP
                                      ELSE runtime_plugin_installations.enabled_at END,
                    disabled_at = CASE WHEN EXCLUDED.status = 'disabled' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    uninstalled_at = NULL, updated_at = CURRENT_TIMESTAMP, updated_by = EXCLUDED.updated_by
                """, pluginId, versionId, environmentCode, status, metadata, status, status,
                operatorId, operatorId);
    }

    /** 查询插件指定安装。 */
    public Optional<Map<String, Object>> findInstallation(Long pluginId, Long installationId) {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id, rpi.plugin_id, rpi.version_id, rpv.version, rpi.environment_code,
                       rpi.status, rpi.load_status, rpi.observed_plugin_code, rpi.observed_version,
                       rpi.observed_artifact_sha256, rpi.last_error, rpi.metadata::text AS metadata,
                       rpi.installed_at, rpi.enabled_at, rpi.disabled_at, rpi.uninstalled_at,
                       rpi.loaded_at, rpi.last_reported_at, rpi.created_at, rpi.updated_at
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id AND rpv.is_deleted = FALSE
                WHERE rpi.id = ? AND rpi.plugin_id = ? AND rpi.is_deleted = FALSE
                """, installationId, pluginId).stream().findFirst();
    }

    /** 查询插件全部环境安装。 */
    public List<Map<String, Object>> findInstallations(Long pluginId) {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id, rpi.plugin_id, rpi.version_id, rpv.version, rpi.environment_code,
                       rpi.status, rpi.load_status, rpi.observed_plugin_code, rpi.observed_version,
                       rpi.observed_artifact_sha256, rpi.last_error, rpi.metadata::text AS metadata,
                       rpi.installed_at, rpi.enabled_at, rpi.disabled_at, rpi.uninstalled_at,
                       rpi.loaded_at, rpi.last_reported_at, rpi.created_at, rpi.updated_at,
                       (SELECT COUNT(1) FROM agent_runtime_plugin_rel arpr
                        WHERE arpr.installation_id = rpi.id AND arpr.is_deleted = FALSE) AS agent_count
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id AND rpv.is_deleted = FALSE
                WHERE rpi.plugin_id = ? AND rpi.is_deleted = FALSE
                ORDER BY rpi.environment_code, rpi.id
                """, pluginId);
    }

    /** 更新安装启停状态并标记需要重启。 */
    public int updateInstallationStatus(Long pluginId,
                                        Long installationId,
                                        String status,
                                        Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE runtime_plugin_installations
                SET status = ?,
                    load_status = 'pending_restart',
                    last_error = NULL,
                    enabled_at = CASE WHEN ? = 'enabled' THEN CURRENT_TIMESTAMP ELSE enabled_at END,
                    disabled_at = CASE WHEN ? = 'disabled' THEN CURRENT_TIMESTAMP ELSE disabled_at END,
                    uninstalled_at = CASE WHEN ? = 'uninstalled' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND plugin_id = ? AND is_deleted = FALSE
                """, status, status, status, status, operatorId, installationId, pluginId);
    }

    /** 统计安装当前 Agent 绑定数。 */
    public int countAgentBindings(Long installationId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM agent_runtime_plugin_rel
                WHERE installation_id = ? AND is_deleted = FALSE
                """, Integer.class, installationId);
        return count == null ? 0 : count;
    }

    /** 查询单个插件的生命周期审计。 */
    public List<Map<String, Object>> findAudits(Long pluginId) {
        return jdbcTemplate.queryForList("""
                SELECT id, plugin_id, version_id, installation_id, agent_id, action,
                       before_state::text AS before_state, after_state::text AS after_state,
                       result, message, operator_id, created_at
                FROM runtime_plugin_lifecycle_audits
                WHERE plugin_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 200
                """, pluginId);
    }

    /** 写入不包含密钥或插件配置正文的生命周期审计。 */
    public void insertAudit(Long pluginId,
                            Long versionId,
                            Long installationId,
                            Long agentId,
                            String action,
                            String beforeState,
                            String afterState,
                            String result,
                            String message,
                            Long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO runtime_plugin_lifecycle_audits(plugin_id, version_id, installation_id,
                    agent_id, action, before_state, after_state, result, message, operator_id)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """, pluginId, versionId, installationId, agentId, action, beforeState,
                afterState, result, message, operatorId);
    }

    /** 服务身份回写加载结果时，从安装记录继承完整租户作用域写入审计。 */
    public int insertLoadResultAudit(Long installationId,
                                     String beforeState,
                                     String afterState,
                                     String message) {
        return jdbcTemplate.update("""
                INSERT INTO runtime_plugin_lifecycle_audits(plugin_id, version_id, installation_id,
                    agent_id, action, before_state, after_state, result, message, operator_id,
                    tenant_id, organization_id, workspace_id, project_id)
                SELECT plugin_id, version_id, id, NULL, 'report_load_result', ?::jsonb, ?::jsonb,
                       'success', ?, NULL, tenant_id, organization_id, workspace_id, project_id
                FROM runtime_plugin_installations
                WHERE id = ? AND is_deleted = FALSE
                """, beforeState, afterState, message, installationId);
    }

    /** 判断当前作用域 Agent 是否存在。 */
    public boolean agentExists(Long agentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM agents WHERE id = ? AND is_deleted = FALSE", Integer.class, agentId);
        return count != null && count > 0;
    }

    /** 查询 Agent 当前插件绑定。 */
    public List<Map<String, Object>> findAgentBindings(Long agentId) {
        return jdbcTemplate.queryForList("""
                SELECT arpr.id, arpr.agent_id, arpr.plugin_id, rp.plugin_code, rp.plugin_name,
                       arpr.installation_id, rpi.environment_code, rpi.status AS installation_status,
                       rpi.load_status, rpi.version_id, rpv.version, arpr.required, arpr.created_at AS bound_at
                FROM agent_runtime_plugin_rel arpr
                JOIN runtime_plugins rp ON rp.id = arpr.plugin_id AND rp.is_deleted = FALSE
                JOIN runtime_plugin_installations rpi
                  ON rpi.id = arpr.installation_id AND rpi.is_deleted = FALSE
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id AND rpv.is_deleted = FALSE
                WHERE arpr.agent_id = ? AND arpr.is_deleted = FALSE
                ORDER BY rp.plugin_code
                """, agentId);
    }

    /** 查询可绑定的已启用插件安装。 */
    public List<Map<String, Object>> findAvailableInstallations() {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id AS installation_id, rp.id AS plugin_id, rp.plugin_code, rp.plugin_name,
                       rpi.version_id, rpv.version, rpi.environment_code, rpi.status,
                       rpi.load_status, rpv.platform_api_version
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugins rp ON rp.id = rpi.plugin_id
                  AND rp.is_deleted = FALSE AND rp.status = 'enabled'
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id
                  AND rpv.is_deleted = FALSE AND rpv.status = 'published'
                WHERE rpi.is_deleted = FALSE AND rpi.status = 'enabled'
                ORDER BY rp.plugin_code, rpi.environment_code
                """);
    }

    /** 查询指定运行环境期望加载的插件安装。 */
    public List<Map<String, Object>> findRuntimeInstallations(String environmentCode) {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id AS installation_id, rpi.plugin_id, rp.plugin_code,
                       rpi.version_id, rpv.version, rpv.platform_api_version,
                       rpv.artifact_uri, rpv.artifact_sha256, rpi.environment_code,
                       rpi.load_status, rpi.last_reported_at
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugins rp ON rp.id = rpi.plugin_id
                  AND rp.is_deleted = FALSE AND rp.status = 'enabled'
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id
                  AND rpv.is_deleted = FALSE AND rpv.status = 'published'
                WHERE rpi.is_deleted = FALSE AND rpi.status = 'enabled'
                  AND rpi.environment_code = ?
                ORDER BY rp.plugin_code, rpi.id
                """, environmentCode);
    }

    /** 按 ID 查询当前仍期望加载的插件安装。 */
    public Optional<Map<String, Object>> findRuntimeInstallationsById(Long installationId) {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id AS installation_id, rpi.plugin_id, rp.plugin_code,
                       rpi.version_id, rpv.version, rpv.platform_api_version,
                       rpv.artifact_uri, rpv.artifact_sha256, rpi.environment_code,
                       rpi.load_status, rpi.last_reported_at
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugins rp ON rp.id = rpi.plugin_id
                  AND rp.is_deleted = FALSE AND rp.status = 'enabled'
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id
                  AND rpv.is_deleted = FALSE AND rpv.status = 'published'
                WHERE rpi.id = ? AND rpi.is_deleted = FALSE AND rpi.status = 'enabled'
                """, installationId).stream().findFirst();
    }

    /** 回写 Core 实际加载状态、观测版本和制品摘要。 */
    public int updateLoadResult(Long installationId,
                                String loadStatus,
                                String observedPluginCode,
                                String observedVersion,
                                String observedArtifactSha256,
                                String lastError) {
        return jdbcTemplate.update("""
                UPDATE runtime_plugin_installations
                SET load_status = ?, observed_plugin_code = ?, observed_version = ?,
                    observed_artifact_sha256 = NULLIF(?, ''), last_error = ?,
                    loaded_at = CASE WHEN ? = 'loaded' THEN CURRENT_TIMESTAMP ELSE loaded_at END,
                    last_reported_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = FALSE AND status = 'enabled'
                """, loadStatus, observedPluginCode, observedVersion, observedArtifactSha256,
                lastError, loadStatus, installationId);
    }

    /** 查询安装是否满足 Agent 绑定前置状态。 */
    public Optional<Map<String, Object>> findBindingCandidate(Long installationId) {
        return jdbcTemplate.queryForList("""
                SELECT rpi.id AS installation_id, rpi.plugin_id, rp.plugin_code,
                       rpi.version_id, rpv.version, rpi.environment_code, rpi.status,
                       rpi.load_status
                FROM runtime_plugin_installations rpi
                JOIN runtime_plugins rp ON rp.id = rpi.plugin_id
                  AND rp.is_deleted = FALSE AND rp.status = 'enabled'
                JOIN runtime_plugin_versions rpv ON rpv.id = rpi.version_id
                  AND rpv.is_deleted = FALSE AND rpv.status = 'published'
                WHERE rpi.id = ? AND rpi.is_deleted = FALSE AND rpi.status = 'enabled'
                """, installationId).stream().findFirst();
    }

    /** 软删除 Agent 的当前插件绑定。 */
    public void softDeleteAgentBindings(Long agentId, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE agent_runtime_plugin_rel
                SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE agent_id = ? AND is_deleted = FALSE
                """, operatorId, agentId);
    }

    /** 新增 Agent 插件绑定。 */
    public void insertAgentBinding(Long agentId,
                                   Long pluginId,
                                   Long installationId,
                                   boolean required,
                                   Long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO agent_runtime_plugin_rel(agent_id, plugin_id, installation_id, required,
                                                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, agentId, pluginId, installationId, required, operatorId, operatorId);
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
}
