package com.zc.agent.registry.modules.runtimeplugin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.capability.RuntimeCapabilityContract;
import com.zc.agent.registry.modules.runtimeplugin.dto.CreateRuntimePluginRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.CreateRuntimePluginVersionRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginDependencyRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallationStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginLoadResultRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginVersionStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.UpdateAgentRuntimePluginsRequest;
import com.zc.agent.registry.modules.runtimeplugin.repository.RuntimePluginRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Runtime Plugin 定义、版本、安装、Agent 绑定和审计服务。 */
@Service
public class RuntimePluginService {

    private static final Set<String> PLUGIN_STATUS = Set.of("enabled", "disabled");
    private static final Set<String> VERSION_STATUS = Set.of("draft", "published", "deprecated");
    private static final Set<String> INSTALL_STATUS = Set.of("enabled", "disabled");
    private static final Set<String> INSTALL_LIFECYCLE_STATUS = Set.of("enabled", "disabled", "uninstalled");
    private static final Set<String> LOAD_RESULT_STATUS = Set.of("loaded", "failed", "not_loaded");
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "tools", "toolProviders", "stepHandlers", "contextProviders", "policies", "hooks", "skills");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final Pattern PLATFORM_API_VERSION_PATTERN = Pattern.compile("^[1-9]\\d*$");
    private static final Pattern ENTRYPOINT_PATTERN = Pattern.compile(
            "^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+$");
    private static final Pattern COMPONENT_CODE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9._:-]*$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern VERSION_COMPARATOR_PATTERN = Pattern.compile("^(?:>=|<=|>|<|=)?(.+)$");
    private static final Pattern VERSION_WILDCARD_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?\\.(?:x|X|\\*)$");

    private final RuntimePluginRepository repository;
    private final ObjectMapper objectMapper;

    public RuntimePluginService(RuntimePluginRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 返回当前项目作用域的插件定义。 */
    public JsonNode listPlugins() {
        return rows(repository.findPlugins());
    }

    /** 返回插件定义、不可变版本、环境安装和生命周期审计。 */
    public JsonNode pluginDetail(Long pluginId) {
        Map<String, Object> plugin = requirePlugin(pluginId);
        ObjectNode result = row(plugin);
        result.set("versions", rows(repository.findVersions(pluginId)));
        result.set("installations", rows(repository.findInstallations(pluginId)));
        result.set("audits", rows(repository.findAudits(pluginId)));
        return result;
    }

    /** 创建项目作用域内唯一的插件定义。 */
    @Transactional
    public JsonNode createPlugin(CreateRuntimePluginRequest request, Long operatorId) {
        validateStatus(PLUGIN_STATUS, request.status(), "插件定义");
        validateJsonObject(request.metadata(), "插件元数据");
        try {
            Long pluginId = repository.insertPlugin(
                    request.pluginCode().trim(), request.pluginName().trim(), trimToNull(request.description()),
                    request.status(), json(request.metadata()), operatorId);
            repository.insertAudit(pluginId, null, null, null, "create_plugin", "{}",
                    json(Map.of("pluginCode", request.pluginCode().trim(), "status", request.status())),
                    "success", "Runtime Plugin 定义创建完成", operatorId);
            return objectMapper.createObjectNode().put("pluginId", pluginId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Runtime Plugin 编码已存在");
        }
    }

    /** 更新插件定义状态；存在启用安装时禁止停用定义。 */
    @Transactional
    public void updatePluginStatus(Long pluginId,
                                   RuntimePluginStatusRequest request,
                                   Long operatorId) {
        validateStatus(PLUGIN_STATUS, request.status(), "插件定义");
        Map<String, Object> plugin = requirePlugin(pluginId);
        if ("disabled".equals(request.status()) && repository.countEnabledInstallations(pluginId) > 0) {
            throw new BusinessException("插件仍存在已启用安装，请先停用环境安装");
        }
        repository.updatePluginStatus(pluginId, request.status(), operatorId);
        repository.insertAudit(pluginId, null, null, null, "update_plugin_status",
                json(Map.of("status", String.valueOf(plugin.get("status")))),
                json(Map.of("status", request.status())), "success", "插件定义状态已更新", operatorId);
    }

    /** 创建内容不可变的插件 Manifest 与制品版本。 */
    @Transactional
    public JsonNode createVersion(Long pluginId,
                                  CreateRuntimePluginVersionRequest request,
                                  Long operatorId) {
        Map<String, Object> plugin = requirePlugin(pluginId);
        validateVersionRequest(String.valueOf(plugin.get("plugin_code")), request);
        ObjectNode manifest = buildManifest(String.valueOf(plugin.get("plugin_code")), request);
        try {
            Long versionId = repository.insertVersion(
                    pluginId, request.version().trim(), request.platformApiVersion().trim(),
                    request.entrypoint().trim(), json(manifest), request.artifactUri().trim(),
                    request.artifactSha256().trim().toLowerCase(), trimToNull(request.releaseNotes()),
                    request.status(), operatorId);
            repository.insertAudit(pluginId, versionId, null, null, "create_version", "{}",
                    json(Map.of("version", request.version().trim(), "status", request.status(),
                            "artifactSha256", request.artifactSha256().trim().toLowerCase())),
                    "success", "Runtime Plugin 版本创建完成", operatorId);
            return objectMapper.createObjectNode().put("pluginId", pluginId).put("versionId", versionId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Runtime Plugin 版本已存在");
        }
    }

    /** 更新版本发布状态，Manifest、入口类和制品信息保持不可变。 */
    @Transactional
    public void updateVersionStatus(Long pluginId,
                                    Long versionId,
                                    RuntimePluginVersionStatusRequest request,
                                    Long operatorId) {
        requirePlugin(pluginId);
        validateStatus(VERSION_STATUS, request.status(), "插件版本");
        Map<String, Object> version = requireVersion(pluginId, versionId);
        validateVersionStatusTransition(String.valueOf(version.get("status")), request.status());
        if ("deprecated".equals(request.status()) && repository.countActiveInstallations(versionId) > 0) {
            throw new BusinessException("插件版本仍存在已启用安装，不能废弃");
        }
        repository.updateVersionStatus(pluginId, versionId, request.status(), operatorId);
        repository.insertAudit(pluginId, versionId, null, null, "update_version_status",
                json(Map.of("status", String.valueOf(version.get("status")))),
                json(Map.of("status", request.status())), "success", "插件版本状态已更新", operatorId);
    }

    /** 安装或替换指定环境的插件版本；当前阶段实际生效需要重启 agent-core。 */
    @Transactional
    public JsonNode install(Long pluginId, RuntimePluginInstallRequest request, Long operatorId) {
        Map<String, Object> plugin = requirePlugin(pluginId);
        if (!"enabled".equals(plugin.get("status"))) {
            throw new BusinessException("Runtime Plugin 定义未启用");
        }
        validateStatus(INSTALL_STATUS, request.status(), "插件安装");
        validateJsonObject(request.metadata(), "安装元数据");
        Map<String, Object> version = requireVersion(pluginId, request.versionId());
        if (!"published".equals(version.get("status"))) {
            throw new BusinessException("只能安装已发布的 Runtime Plugin 版本");
        }
        String environmentCode = request.environmentCode().trim();
        Map<String, Object> before = repository.findInstallationByEnvironment(pluginId, environmentCode)
                .orElse(Map.of());
        validateInstallationReplacement(before, request);
        Long installationId = repository.upsertInstallation(
                pluginId, request.versionId(), environmentCode, request.status(),
                json(request.metadata()), operatorId);
        repository.insertAudit(pluginId, request.versionId(), installationId, null, "install",
                safeInstallationState(before),
                json(Map.of("versionId", request.versionId(), "version", String.valueOf(version.get("version")),
                        "environmentCode", environmentCode, "status", request.status(),
                        "loadStatus", "pending_restart")),
                "success", "插件安装已登记，重启 agent-core 后生效", operatorId);
        return objectMapper.createObjectNode().put("pluginId", pluginId)
                .put("versionId", request.versionId()).put("installationId", installationId)
                .put("loadStatus", "pending_restart");
    }

    private void validateInstallationReplacement(Map<String, Object> current,
                                                 RuntimePluginInstallRequest request) {
        if (current.isEmpty()) {
            return;
        }
        Long installationId = ((Number) current.get("id")).longValue();
        Long currentVersionId = ((Number) current.get("version_id")).longValue();
        boolean changesBoundRuntime = !currentVersionId.equals(request.versionId())
                || !"enabled".equals(request.status());
        if (changesBoundRuntime && repository.countAgentBindings(installationId) > 0) {
            throw new BusinessException("插件安装仍被 Agent 绑定，请先解除绑定再替换版本或停用");
        }
    }

    /** 更新安装启停状态；存在 Agent 绑定时禁止停用或卸载。 */
    @Transactional
    public void updateInstallationStatus(Long pluginId,
                                         Long installationId,
                                         RuntimePluginInstallationStatusRequest request,
                                         Long operatorId) {
        Map<String, Object> plugin = requirePlugin(pluginId);
        validateStatus(INSTALL_LIFECYCLE_STATUS, request.status(), "插件安装");
        Map<String, Object> installation = requireInstallation(pluginId, installationId);
        if (!"enabled".equals(request.status()) && repository.countAgentBindings(installationId) > 0) {
            throw new BusinessException("插件安装仍被 Agent 绑定，请先解除绑定");
        }
        if ("enabled".equals(request.status())) {
            if (!"enabled".equals(plugin.get("status"))) {
                throw new BusinessException("Runtime Plugin 定义未启用");
            }
            Map<String, Object> version = requireVersion(pluginId,
                    ((Number) installation.get("version_id")).longValue());
            if (!"published".equals(version.get("status"))) {
                throw new BusinessException("只能启用已发布的 Runtime Plugin 版本");
            }
        }
        repository.updateInstallationStatus(pluginId, installationId, request.status(), operatorId);
        String loadStatus = "pending_restart";
        repository.insertAudit(pluginId, ((Number) installation.get("version_id")).longValue(),
                installationId, null, "update_installation_status", safeInstallationState(installation),
                json(Map.of("status", request.status(), "loadStatus", loadStatus)), "success",
                "插件安装状态已更新", operatorId);
    }

    /** 返回 Agent 当前绑定和所有可绑定插件安装。 */
    public JsonNode agentBindings(Long agentId) {
        ensureAgent(agentId);
        ObjectNode result = objectMapper.createObjectNode().put("agentId", agentId);
        result.set("bindings", rows(repository.findAgentBindings(agentId)));
        result.set("availableInstallations", rows(repository.findAvailableInstallations()));
        return result;
    }

    /** 返回指定环境期望由 agent-core 加载的插件安装。 */
    public JsonNode runtimeInstallations(String environmentCode) {
        if (environmentCode == null || environmentCode.isBlank() || environmentCode.length() > 64) {
            throw new BusinessException("运行环境编码不合法");
        }
        return rows(repository.findRuntimeInstallations(environmentCode.trim()));
    }

    /** 接收 agent-core 的真实加载结果并写入安全审计。 */
    @Transactional
    public void reportLoadResult(Long installationId, RuntimePluginLoadResultRequest request) {
        validateStatus(LOAD_RESULT_STATUS, request.loadStatus(), "插件加载");
        Map<String, Object> installation = repository.findRuntimeInstallationsById(installationId)
                .orElseThrow(() -> new BusinessException("待观测的 Runtime Plugin 安装不存在或未启用"));
        String error = "loaded".equals(request.loadStatus()) ? null : formatLoadError(request);
        int updated = repository.updateLoadResult(
                installationId,
                request.loadStatus(),
                trimToNull(request.observedPluginCode()),
                trimToNull(request.observedVersion()),
                request.observedArtifactSha256() == null ? "" : request.observedArtifactSha256().trim(),
                error
        );
        if (updated <= 0) {
            throw new BusinessException("Runtime Plugin 安装状态已变化，请重新同步");
        }
        repository.insertLoadResultAudit(installationId,
                json(Map.of("loadStatus", String.valueOf(installation.get("load_status")))),
                loadResultAuditState(request), "agent-core 已回写插件加载结果");
    }

    private String formatLoadError(RuntimePluginLoadResultRequest request) {
        String errorCode = trimToNull(request.errorCode());
        String errorMessage = trimToNull(request.errorMessage());
        if (errorCode == null) {
            return errorMessage;
        }
        return errorMessage == null ? errorCode : errorCode + ": " + errorMessage;
    }

    private String loadResultAuditState(RuntimePluginLoadResultRequest request) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("loadStatus", request.loadStatus());
        putNullable(state, "observedPluginCode", request.observedPluginCode());
        putNullable(state, "observedVersion", request.observedVersion());
        putNullable(state, "observedArtifactSha256", request.observedArtifactSha256());
        putNullable(state, "errorCode", request.errorCode());
        return json(state);
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
        } else {
            node.put(field, value.trim());
        }
    }

    /** 覆盖更新 Agent 插件绑定，并逐插件记录绑定变化审计。 */
    @Transactional
    public JsonNode updateAgentBindings(Long agentId,
                                        UpdateAgentRuntimePluginsRequest request,
                                        Long operatorId) {
        ensureAgent(agentId);
        LinkedHashMap<Long, BindingCandidate> candidates = validateBindingCandidates(request);
        List<Map<String, Object>> before = repository.findAgentBindings(agentId);
        repository.softDeleteAgentBindings(agentId, operatorId);
        candidates.values().forEach(candidate -> repository.insertAgentBinding(
                agentId, candidate.pluginId(), candidate.installationId(), candidate.required(), operatorId));
        List<Map<String, Object>> after = repository.findAgentBindings(agentId);
        auditBindingChanges(agentId, before, after, operatorId);
        return agentBindings(agentId);
    }

    private LinkedHashMap<Long, BindingCandidate> validateBindingCandidates(
            UpdateAgentRuntimePluginsRequest request) {
        LinkedHashMap<Long, BindingCandidate> candidates = new LinkedHashMap<>();
        Set<Long> installationIds = new LinkedHashSet<>();
        for (UpdateAgentRuntimePluginsRequest.AgentRuntimePluginBindingRequest binding : request.bindings()) {
            if (!installationIds.add(binding.installationId())) {
                throw new BusinessException("Agent 插件安装绑定重复: " + binding.installationId());
            }
            Map<String, Object> candidate = repository.findBindingCandidate(binding.installationId())
                    .orElseThrow(() -> new BusinessException(
                            "插件安装不存在、未启用或版本未发布: " + binding.installationId()));
            Long pluginId = ((Number) candidate.get("plugin_id")).longValue();
            if (candidates.containsKey(pluginId)) {
                throw new BusinessException("同一 Agent 不能绑定同一插件的多个环境安装");
            }
            candidates.put(pluginId, new BindingCandidate(
                    pluginId, binding.installationId(), binding.required()));
        }
        return candidates;
    }

    private void auditBindingChanges(Long agentId,
                                     List<Map<String, Object>> before,
                                     List<Map<String, Object>> after,
                                     Long operatorId) {
        Map<Long, Map<String, Object>> beforeByPlugin = indexBindings(before);
        Map<Long, Map<String, Object>> afterByPlugin = indexBindings(after);
        Set<Long> pluginIds = new LinkedHashSet<>(beforeByPlugin.keySet());
        pluginIds.addAll(afterByPlugin.keySet());
        for (Long pluginId : pluginIds) {
            Map<String, Object> oldBinding = beforeByPlugin.getOrDefault(pluginId, Map.of());
            Map<String, Object> newBinding = afterByPlugin.getOrDefault(pluginId, Map.of());
            String action = newBinding.isEmpty() ? "unbind_agent" : "bind_agent";
            Long installationId = newBinding.isEmpty() ? null
                    : ((Number) newBinding.get("installation_id")).longValue();
            Long versionId = newBinding.isEmpty() ? null
                    : ((Number) newBinding.get("version_id")).longValue();
            repository.insertAudit(pluginId, versionId, installationId, agentId, action,
                    safeBindingState(oldBinding), safeBindingState(newBinding), "success",
                    newBinding.isEmpty() ? "Agent 插件绑定已解除" : "Agent 插件绑定已更新", operatorId);
        }
    }

    private Map<Long, Map<String, Object>> indexBindings(List<Map<String, Object>> bindings) {
        LinkedHashMap<Long, Map<String, Object>> result = new LinkedHashMap<>();
        bindings.forEach(binding -> result.put(((Number) binding.get("plugin_id")).longValue(), binding));
        return result;
    }

    private String safeBindingState(Map<String, Object> state) {
        if (state.isEmpty()) {
            return "{}";
        }
        return json(Map.of(
                "pluginId", state.get("plugin_id"),
                "installationId", state.get("installation_id"),
                "versionId", state.get("version_id"),
                "environmentCode", state.get("environment_code"),
                "required", state.get("required")
        ));
    }

    private String safeInstallationState(Map<String, Object> state) {
        if (state.isEmpty()) {
            return "{}";
        }
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        copyIfPresent(state, safe, "version_id");
        copyIfPresent(state, safe, "environment_code");
        copyIfPresent(state, safe, "status");
        copyIfPresent(state, safe, "load_status");
        return json(safe);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private void validateVersionRequest(String pluginCode, CreateRuntimePluginVersionRequest request) {
        requireMatch(request.version(), SEMVER_PATTERN, "插件版本不是合法 SemVer");
        requireMatch(request.platformApiVersion(), PLATFORM_API_VERSION_PATTERN, "平台 API 版本不合法");
        requireMatch(request.entrypoint(), ENTRYPOINT_PATTERN, "插件入口类不合法");
        requireMatch(request.artifactSha256(), SHA256_PATTERN, "插件制品 SHA-256 不合法");
        if (request.artifactUri() == null || request.artifactUri().isBlank()) {
            throw new BusinessException("插件制品地址不能为空");
        }
        RuntimeCapabilityContract.normalizeCapabilities(request.capabilities());
        Set<String> dependencyCodes = new LinkedHashSet<>();
        for (RuntimePluginDependencyRequest dependency : request.dependencies()) {
            if (!dependencyCodes.add(dependency.pluginCode())) {
                throw new BusinessException("插件依赖重复: " + dependency.pluginCode());
            }
            if (pluginCode.equals(dependency.pluginCode())) {
                throw new BusinessException("插件不能依赖自身");
            }
            validateVersionConstraint(dependency.versionConstraint());
            if (!dependency.optional() && !repository.pluginExistsByCode(dependency.pluginCode())) {
                throw new BusinessException("必需依赖插件尚未注册: " + dependency.pluginCode());
            }
        }
        for (Map.Entry<String, List<String>> entry : request.components().entrySet()) {
            if (!COMPONENT_TYPES.contains(entry.getKey())) {
                throw new BusinessException("不支持的插件组件类型: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue().size() > 500) {
                throw new BusinessException("插件组件数量不合法: " + entry.getKey());
            }
            validateUniqueValues(entry.getValue(), COMPONENT_CODE_PATTERN, "插件组件 " + entry.getKey());
        }
        validateStatus(VERSION_STATUS, request.status(), "插件版本");
    }

    private void validateUniqueValues(List<String> values, Pattern pattern, String label) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            requireMatch(value, pattern, label + "编码不合法");
            if (!unique.add(value.trim())) {
                throw new BusinessException(label + "存在重复值: " + value.trim());
            }
        }
    }

    private void validateVersionConstraint(String constraint) {
        String[] alternatives = constraint.trim().split("\\s*\\|\\|\\s*", -1);
        for (String alternative : alternatives) {
            if (alternative.isBlank()) {
                throw new BusinessException("插件依赖版本约束不合法: " + constraint);
            }
            if ("*".equals(alternative) || VERSION_WILDCARD_PATTERN.matcher(alternative).matches()) {
                continue;
            }
            if ((alternative.startsWith("^") || alternative.startsWith("~"))
                    && !alternative.contains(" ")) {
                requireMatch(alternative.substring(1), SEMVER_PATTERN,
                        "插件依赖版本约束不合法: " + constraint);
                continue;
            }
            for (String token : alternative.split("\\s+")) {
                var matcher = VERSION_COMPARATOR_PATTERN.matcher(token);
                if (!matcher.matches() || !SEMVER_PATTERN.matcher(matcher.group(1)).matches()) {
                    throw new BusinessException("插件依赖版本约束不合法: " + constraint);
                }
            }
        }
    }

    private void validateVersionStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus)) {
            return;
        }
        boolean allowed = ("draft".equals(currentStatus) && "published".equals(targetStatus))
                || ("published".equals(currentStatus) && "deprecated".equals(targetStatus))
                || ("deprecated".equals(currentStatus) && "published".equals(targetStatus));
        if (!allowed) {
            throw new BusinessException("插件版本状态不允许从 " + currentStatus + " 变更为 " + targetStatus);
        }
    }

    private ObjectNode buildManifest(String pluginCode, CreateRuntimePluginVersionRequest request) {
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("pluginCode", pluginCode);
        manifest.put("version", request.version().trim());
        manifest.put("platformApiVersion", request.platformApiVersion().trim());
        manifest.put("entrypoint", request.entrypoint().trim());
        manifest.set("capabilities", objectMapper.valueToTree(
                request.capabilities().stream().map(String::trim).toList()));
        ArrayNode dependencies = manifest.putArray("dependencies");
        request.dependencies().forEach(item -> dependencies.addObject()
                .put("pluginCode", item.pluginCode().trim())
                .put("versionConstraint", item.versionConstraint().trim())
                .put("optional", item.optional()));
        ObjectNode components = manifest.putObject("components");
        request.components().forEach((type, codes) -> components.set(
                type, objectMapper.valueToTree(codes.stream().map(String::trim).toList())));
        return manifest;
    }

    private Map<String, Object> requirePlugin(Long pluginId) {
        return repository.findPlugin(pluginId)
                .orElseThrow(() -> new BusinessException("Runtime Plugin 不存在"));
    }

    private Map<String, Object> requireVersion(Long pluginId, Long versionId) {
        return repository.findVersion(pluginId, versionId)
                .orElseThrow(() -> new BusinessException("Runtime Plugin 版本不存在"));
    }

    private Map<String, Object> requireInstallation(Long pluginId, Long installationId) {
        return repository.findInstallation(pluginId, installationId)
                .orElseThrow(() -> new BusinessException("Runtime Plugin 安装不存在"));
    }

    private void ensureAgent(Long agentId) {
        if (!repository.agentExists(agentId)) {
            throw new BusinessException("Agent 不存在");
        }
    }

    private void validateStatus(Set<String> allowed, String status, String label) {
        if (!allowed.contains(status)) {
            throw new BusinessException(label + "状态不合法: " + status);
        }
    }

    private void validateJsonObject(JsonNode node, String label) {
        if (node != null && !node.isNull() && !node.isObject()) {
            throw new BusinessException(label + "必须是 JSON 对象");
        }
    }

    private void requireMatch(String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value.trim()).matches()) {
            throw new BusinessException(message);
        }
    }

    private ArrayNode rows(List<Map<String, Object>> source) {
        ArrayNode result = objectMapper.createArrayNode();
        source.forEach(item -> result.add(row(item)));
        return result;
    }

    private ObjectNode row(Map<String, Object> source) {
        ObjectNode target = objectMapper.createObjectNode();
        source.forEach((key, value) -> {
            if (value == null) {
                target.putNull(key);
            } else if (isJsonColumn(key)) {
                target.set(key, parse(value.toString()));
            } else {
                target.set(key, objectMapper.valueToTree(value));
            }
        });
        return target;
    }

    private boolean isJsonColumn(String key) {
        return "metadata".equals(key) || "manifest".equals(key)
                || "before_state".equals(key) || "after_state".equals(key);
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(Object value) {
        try {
            Object source = value == null ? objectMapper.createObjectNode() : value;
            return objectMapper.writeValueAsString(source);
        } catch (Exception exception) {
            throw new BusinessException("Runtime Plugin JSON 序列化失败");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record BindingCandidate(Long pluginId, Long installationId, boolean required) {
    }
}
