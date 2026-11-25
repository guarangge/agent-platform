package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.capability.ToolRuntimeCapabilityInference;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.tool.dto.ToolCatalogRequest;
import com.zc.agent.registry.modules.tool.dto.ToolDeploymentRequest;
import com.zc.agent.registry.modules.tool.dto.ToolDiscoveryRequest;
import com.zc.agent.registry.modules.tool.dto.ToolMigrationRequest;
import com.zc.agent.registry.modules.tool.dto.ToolInstallRequest;
import com.zc.agent.registry.modules.tool.dto.ToolRouteUpdateRequest;
import com.zc.agent.registry.modules.tool.dto.ToolVersionRequest;
import com.zc.agent.registry.modules.tool.repository.ToolLifecycleRepository;
import com.zc.agent.registry.modules.tool.repository.ToolRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tool 安装、版本、部署、迁移、下线与审计的统一生命周期服务。 */
@Service
public class ToolLifecycleService {

    private static final Set<String> DEPLOYMENT_STATUS = Set.of("enabled", "disabled", "draining");
    private static final Set<String> VERSION_STATUS = Set.of("draft", "published", "deprecated", "retired");

    private final ToolLifecycleRepository repository;
    private final ToolRepository toolRepository;
    private final ToolRemoteDiscoveryService discoveryService;
    private final ToolSecretCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public ToolLifecycleService(ToolLifecycleRepository repository,
                                ToolRepository toolRepository,
                                ToolRemoteDiscoveryService discoveryService,
                                ToolSecretCryptoService cryptoService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.toolRepository = toolRepository;
        this.discoveryService = discoveryService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    public JsonNode listCatalogs() {
        return rows(repository.findCatalogs(), false);
    }

    public JsonNode globalAudits() {
        return rows(repository.findGlobalAudits(), false);
    }

    @Transactional
    public JsonNode createCatalog(ToolCatalogRequest request, Long operatorId) {
        try {
            Long id = repository.insertCatalog(request.catalogCode().trim(), request.catalogName().trim(),
                    request.catalogType().trim(), request.sourceUrl(), request.transportType(), json(request.config()),
                    request.status(), operatorId);
            return objectMapper.createObjectNode().put("catalogId", id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Catalog 编码已存在");
        }
    }

    @Transactional
    public JsonNode discover(ToolDiscoveryRequest request, Long operatorId) {
        try {
            List<ToolRemoteDiscoveryService.RemoteTool> remoteTools = discoveryService.discover(request);
            if (request.catalogId() != null) {
                repository.replaceCatalogItems(request.catalogId(), remoteTools.stream()
                        .map(item -> new ToolLifecycleRepository.DiscoveredItem(item.name(), item.displayName(),
                                item.description(), json(item.inputSchema()), item.version(), json(item.metadata())))
                        .toList());
            }
            ArrayNode items = objectMapper.createArrayNode();
            if (request.catalogId() != null) {
                repository.findCatalogItems(request.catalogId()).forEach(item -> items.add(row(item, false)));
            } else {
                remoteTools.forEach(item -> items.add(objectMapper.valueToTree(Map.of(
                        "remote_name", item.name(), "display_name", item.displayName(),
                        "description", item.description(), "input_schema", item.inputSchema(),
                        "remote_version", item.version() == null ? "" : item.version(), "metadata", item.metadata()))));
            }
            repository.insertAudit(null, null, "discover", "{}", json(items), "success",
                    "发现 " + remoteTools.size() + " 个远程 Tool", operatorId);
            return objectMapper.createObjectNode().put("count", remoteTools.size()).set("items", items);
        } catch (RuntimeException exception) {
            repository.markCatalogDiscoveryFailed(request.catalogId(), exception.getMessage());
            repository.insertAudit(null, null, "discover", "{}", "{}", "failed", exception.getMessage(), operatorId);
            throw exception;
        }
    }

    public JsonNode catalogItems(Long catalogId) {
        return rows(repository.findCatalogItems(catalogId), false);
    }

    /** 将远程发现条目安装为稳定 Tool 标识、基线版本和可路由部署实例。 */
    @Transactional
    public JsonNode install(ToolInstallRequest request, Long operatorId) {
        Map<String, Object> item = repository.findCatalogItem(request.catalogItemId())
                .orElseThrow(() -> new BusinessException("Catalog Tool 条目不存在"));
        validateTransport(request.providerType(), request.transportType(), request.endpoint(), request.invokeConfig());
        try {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("catalogItemId", request.catalogItemId());
            String remoteName = String.valueOf(item.get("remote_name"));
            metadata.put("remoteName", remoteName);
            ToolRuntimeCapabilityInference.Declaration declaration =
                    ToolRuntimeCapabilityInference.infer(request.providerType(), remoteName);
            if (!"none".equals(declaration.sideEffectLevel())) {
                metadata.putObject("sideEffectPolicy")
                        .put("level", declaration.sideEffectLevel())
                        .put("idempotencyMode", "generated");
            }
            Long toolId = repository.insertInstalledTool(request.toolCode(), request.toolName(), request.toolType(),
                    request.toolCategory(), (String) item.get("description"), String.valueOf(item.get("input_schema")),
                    request.permissionLevel(), json(objectMapper.valueToTree(declaration.capabilities())),
                    declaration.sideEffectLevel(), json(metadata), operatorId);
            Long versionId = repository.insertVersion(toolId, request.version(), String.valueOf(item.get("input_schema")),
                    "{}", json(request.invokeConfig()), "从 Catalog 安装", "compatible", "published", operatorId);
            Long deploymentId = repository.insertDeployment(toolId, versionId, request.toolCode() + "_" + request.environmentCode(),
                    request.toolName() + " " + request.environmentCode(), request.environmentCode(), request.providerType(),
                    request.transportType(), request.endpoint(), request.authType(),
                    cryptoService.encryptJsonText(json(request.authConfig())),
                    cryptoService.encryptJsonText(json(request.headers())), json(request.invokeConfig()),
                    100, 100, 100, 30, 0, 3, 60, "enabled", "{}", operatorId);
            bindInstalledTool(request, item, toolId, operatorId);
            repository.insertAudit(toolId, deploymentId, "install", "{}", json(objectMapper.valueToTree(request)),
                    "success", "Catalog Tool 安装完成", operatorId);
            return objectMapper.createObjectNode().put("toolId", toolId).put("versionId", versionId)
                    .put("deploymentId", deploymentId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Tool 编码或部署实例编码已存在");
        }
    }

    /**
     * Lifecycle Deployment 负责实例路由，Tool Binding 负责提供稳定的远程 operationName。
     * 两者必须在同一安装事务中创建，否则 Tool 虽显示已安装，Agent 运行时仍无法调用远程操作。
     */
    private void bindInstalledTool(ToolInstallRequest request,
                                   Map<String, Object> item,
                                   Long toolId,
                                   Long operatorId) {
        String catalogId = String.valueOf(item.get("catalog_id"));
        String providerCode = "catalog_" + catalogId + "_" + request.providerType();
        String instanceCode = request.toolCode() + "_" + request.environmentCode() + "_binding";
        Long providerId = toolRepository.upsertToolProvider(
                providerCode,
                "Catalog " + catalogId + " " + request.providerType(),
                request.providerType(),
                request.transportType(),
                "{}",
                "enabled",
                operatorId
        );
        Long instanceId = toolRepository.upsertToolProviderInstance(
                providerId,
                instanceCode,
                request.toolName() + " " + request.environmentCode(),
                request.environmentCode(),
                request.endpoint(),
                request.authType(),
                cryptoService.encryptJsonText(json(request.authConfig())),
                cryptoService.encryptJsonText(json(request.headers())),
                30,
                0,
                "enabled",
                operatorId
        );
        toolRepository.upsertToolBinding(
                toolId,
                providerId,
                instanceId,
                String.valueOf(item.get("remote_name")),
                "mcp".equalsIgnoreCase(request.providerType()) ? "mcp_tool" : "http_api",
                json(request.invokeConfig()),
                "{}",
                "{}",
                "enabled",
                operatorId
        );
    }

    public JsonNode lifecycle(Long toolId) {
        ensureTool(toolId);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("versions", rows(repository.findVersions(toolId), false));
        result.set("deployments", rows(repository.findDeployments(toolId), false));
        result.set("migrations", rows(repository.findMigrations(toolId), false));
        result.set("audits", rows(repository.findAudits(toolId), false));
        result.set("impact", objectMapper.valueToTree(repository.dependencyImpact(toolId)));
        return result;
    }

    @Transactional
    public JsonNode createVersion(Long toolId, ToolVersionRequest request, Long operatorId) {
        ensureTool(toolId);
        validateStatus(VERSION_STATUS, request.status(), "版本");
        try {
            Long id = repository.insertVersion(toolId, request.version().trim(), json(request.inputSchema()),
                    json(request.outputSchema()), json(request.invokeConfig()), request.releaseNotes(),
                    request.compatibility(), request.status(), operatorId);
            repository.insertAudit(toolId, null, "version_create", "{}",
                    json(objectMapper.valueToTree(request)), "success", "创建 Tool 版本 " + request.version(), operatorId);
            return objectMapper.createObjectNode().put("versionId", id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("Tool 版本号已存在");
        }
    }

    @Transactional
    public JsonNode createDeployment(Long toolId, ToolDeploymentRequest request, Long operatorId) {
        ensureTool(toolId);
        repository.findVersion(toolId, request.versionId()).orElseThrow(() -> new BusinessException("Tool 版本不存在"));
        validateStatus(DEPLOYMENT_STATUS, request.status(), "部署实例");
        validateTransport(request.providerType(), request.transportType(), request.endpoint(), request.invokeConfig());
        try {
            Long id = repository.insertDeployment(toolId, request.versionId(), request.deploymentCode(),
                    request.deploymentName(), request.environmentCode(), request.providerType(), request.transportType(),
                    request.endpoint(), request.authType(), cryptoService.encryptJsonText(json(request.authConfig())),
                    cryptoService.encryptJsonText(json(request.headers())), json(request.invokeConfig()),
                    defaultInt(request.trafficWeight(), 100), defaultInt(request.rolloutPercent(), 100),
                    defaultInt(request.priority(), 100), defaultInt(request.timeoutSeconds(), 30),
                    defaultInt(request.retryTimes(), 0), defaultInt(request.failureThreshold(), 3),
                    defaultInt(request.recoverySeconds(), 60), request.status(), json(request.metadata()), operatorId);
            repository.insertAudit(toolId, id, "deployment_create", "{}",
                    json(objectMapper.valueToTree(request)), "success", "创建部署实例", operatorId);
            return objectMapper.createObjectNode().put("deploymentId", id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("部署实例编码已存在");
        }
    }

    @Transactional
    public void updateRoute(Long toolId, Long deploymentId, ToolRouteUpdateRequest request, Long operatorId) {
        Map<String, Object> before = repository.findDeployment(toolId, deploymentId)
                .orElseThrow(() -> new BusinessException("Tool 部署实例不存在"));
        validateStatus(DEPLOYMENT_STATUS, request.status(), "部署实例");
        int updated = repository.updateRoute(toolId, deploymentId, defaultInt(request.trafficWeight(), 100),
                defaultInt(request.rolloutPercent(), 100), defaultInt(request.priority(), 100),
                defaultInt(request.failureThreshold(), 3), defaultInt(request.recoverySeconds(), 60),
                request.status(), operatorId);
        if (updated == 0) {
            throw new BusinessException("Tool 部署实例不存在");
        }
        repository.insertAudit(toolId, deploymentId, "route_update", json(objectMapper.valueToTree(before)),
                json(objectMapper.valueToTree(request)), "success", "更新灰度流量与实例状态", operatorId);
    }

    @Transactional
    public JsonNode migrate(Long toolId, ToolMigrationRequest request, Long operatorId) {
        ensureTool(toolId);
        Map<String, Object> target = repository.findVersion(toolId, request.targetVersionId())
                .orElseThrow(() -> new BusinessException("目标版本不存在"));
        Map<String, Object> source = request.sourceVersionId() == null ? null
                : repository.findVersion(toolId, request.sourceVersionId())
                .orElseThrow(() -> new BusinessException("源版本不存在"));
        ObjectNode validation = validateCompatibility(source, target);
        if (!validation.path("compatible").asBoolean() && !"force".equals(request.strategy())) {
            throw new BusinessException("目标版本存在不兼容输入变更，请使用 force 策略或修正 Schema");
        }
        int rollout = defaultInt(request.rolloutPercent(), "immediate".equals(request.strategy()) ? 100 : 10);
        List<Map<String, Object>> deployments = repository.findDeployments(toolId);
        boolean targetDeployed = deployments.stream().anyMatch(item -> request.targetVersionId()
                .equals(((Number) item.get("version_id")).longValue()));
        if (!targetDeployed) {
            throw new BusinessException("目标版本尚未部署实例，不能开始流量迁移");
        }
        for (Map<String, Object> deployment : deployments) {
            Long deploymentId = ((Number) deployment.get("id")).longValue();
            Long versionId = ((Number) deployment.get("version_id")).longValue();
            if (request.targetVersionId().equals(versionId)) {
                repository.updateRoute(toolId, deploymentId,
                        ((Number) deployment.get("traffic_weight")).intValue(), rollout,
                        ((Number) deployment.get("priority")).intValue(),
                        ((Number) deployment.get("failure_threshold")).intValue(),
                        ((Number) deployment.get("recovery_seconds")).intValue(), "enabled", operatorId);
            } else if ("immediate".equals(request.strategy()) &&
                    (request.sourceVersionId() == null || request.sourceVersionId().equals(versionId))) {
                repository.updateRoute(toolId, deploymentId, 0, 0,
                        ((Number) deployment.get("priority")).intValue(),
                        ((Number) deployment.get("failure_threshold")).intValue(),
                        ((Number) deployment.get("recovery_seconds")).intValue(), "draining", operatorId);
            }
        }
        String status = rollout >= 100 ? "completed" : "running";
        Long migrationId = repository.insertMigration(toolId, request.sourceVersionId(), request.targetVersionId(),
                request.strategy(), rollout, status, json(validation), operatorId);
        repository.insertAudit(toolId, null, "version_migrate", source == null ? "{}" : json(objectMapper.valueToTree(source)),
                json(objectMapper.valueToTree(target)), "success", "版本迁移策略=" + request.strategy(), operatorId);
        return objectMapper.createObjectNode().put("migrationId", migrationId).put("status", status)
                .set("validation", validation);
    }

    public JsonNode impact(Long toolId) {
        ensureTool(toolId);
        ObjectNode result = objectMapper.valueToTree(repository.dependencyImpact(toolId));
        long agents = result.path("agent_count").asLong();
        long workflows = result.path("workflow_count").asLong();
        result.put("safeToRetire", agents == 0 && workflows == 0);
        result.put("recommendation", agents + workflows > 0 ? "先迁移依赖并将实例置为 draining" : "可以进入 draining 后下线");
        return result;
    }

    @Transactional
    public void retire(Long toolId, Long operatorId) {
        ObjectNode impact = (ObjectNode) impact(toolId);
        if (!impact.path("safeToRetire").asBoolean()) {
            throw new BusinessException("Tool 仍被 Agent 或 Workflow 依赖，不能下线");
        }
        if (impact.path("active_deployment_count").asLong() > 0) {
            throw new BusinessException("仍有 enabled/draining 实例，请先完成流量排空并停用实例");
        }
        repository.retireTool(toolId, operatorId);
        repository.insertAudit(toolId, null, "retire", "{}", "{}", "success", "Tool 已安全下线", operatorId);
    }

    /** 仅供 agent-core 使用，返回解密后的可路由实例。 */
    public JsonNode runtimeRouting(Long toolId) {
        ensureTool(toolId);
        ArrayNode routes = objectMapper.createArrayNode();
        for (Map<String, Object> row : repository.findDeployments(toolId)) {
            if (!"enabled".equals(row.get("status"))) {
                continue;
            }
            ObjectNode route = row(row, true);
            routes.add(route);
        }
        return objectMapper.createObjectNode().put("toolId", toolId).put("managed", true).set("routes", routes);
    }

    private ObjectNode validateCompatibility(Map<String, Object> source, Map<String, Object> target) {
        Set<String> sourceRequired = requiredFields(source == null ? null : source.get("input_schema"));
        Set<String> targetRequired = requiredFields(target.get("input_schema"));
        Set<String> addedRequired = new HashSet<>(targetRequired);
        addedRequired.removeAll(sourceRequired);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("compatible", addedRequired.isEmpty());
        result.set("addedRequiredFields", objectMapper.valueToTree(addedRequired));
        return result;
    }

    private Set<String> requiredFields(Object schemaValue) {
        try {
            JsonNode schema = objectMapper.readTree(schemaValue == null ? "{}" : schemaValue.toString());
            Set<String> result = new HashSet<>();
            schema.path("required").forEach(item -> result.add(item.asText()));
            return result;
        } catch (Exception exception) {
            return Set.of();
        }
    }

    private ArrayNode rows(List<Map<String, Object>> rows, boolean sensitive) {
        ArrayNode result = objectMapper.createArrayNode();
        rows.forEach(item -> result.add(row(item, sensitive)));
        return result;
    }

    private ObjectNode row(Map<String, Object> source, boolean sensitive) {
        ObjectNode target = objectMapper.createObjectNode();
        source.forEach((key, value) -> {
            if (value == null) {
                target.putNull(key);
            } else if (isJsonColumn(key)) {
                JsonNode parsed = parse(value.toString());
                if (("auth_config".equals(key) || "headers".equals(key))) {
                    parsed = cryptoService.decryptJsonText(value.toString());
                    if (!sensitive) {
                        parsed = cryptoService.maskJson(parsed);
                    }
                }
                target.set(key, parsed);
            } else {
                target.set(key, objectMapper.valueToTree(value));
            }
        });
        return target;
    }

    private boolean isJsonColumn(String key) {
        return key.endsWith("schema") || key.endsWith("config") || key.equals("metadata")
                || key.equals("before_state") || key.equals("after_state") || key.equals("validation_result")
                || key.equals("headers");
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isNull() ? objectMapper.createObjectNode() : node);
        } catch (Exception exception) {
            throw new BusinessException("Tool 生命周期 JSON 序列化失败");
        }
    }

    private void validateTransport(String providerType, String transport, String endpoint, JsonNode invokeConfig) {
        String normalized = transport == null ? "" : transport.trim().toLowerCase();
        if ("stdio".equals(normalized) && (invokeConfig == null || invokeConfig.path("command").isMissingNode())) {
            throw new BusinessException("stdio 部署必须配置 invokeConfig.command");
        }
        if (!"internal".equalsIgnoreCase(providerType) && !"stdio".equals(normalized)
                && (endpoint == null || endpoint.isBlank())) {
            throw new BusinessException("远程 Tool 部署 endpoint 不能为空");
        }
    }

    private void validateStatus(Set<String> allowed, String status, String label) {
        if (!allowed.contains(status)) {
            throw new BusinessException(label + "状态不合法: " + status);
        }
    }

    private void ensureTool(Long toolId) {
        if (!repository.toolExists(toolId)) {
            throw new BusinessException("Tool 不存在");
        }
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
