package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.capability.RuntimeCapabilityContract;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.tool.dto.CreateToolRequest;
import com.zc.agent.registry.modules.tool.dto.ToolPageQueryRequest;
import com.zc.agent.registry.modules.tool.dto.UpdateToolRequest;
import com.zc.agent.registry.modules.tool.model.ToolDetailRecord;
import com.zc.agent.registry.modules.tool.repository.ToolRepository;
import com.zc.agent.registry.modules.tool.vo.ToolDetailResponse;
import com.zc.agent.registry.modules.tool.vo.ToolPageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tool 服务。
 */
@Service
public class ToolService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_DRAFT, STATUS_ENABLED, STATUS_DISABLED);

    private final ToolRepository toolRepository;
    private final DictService dictService;
    private final ObjectMapper objectMapper;
    private final ToolSecretCryptoService toolSecretCryptoService;

    public ToolService(ToolRepository toolRepository,
                       DictService dictService,
                       ObjectMapper objectMapper,
                       ToolSecretCryptoService toolSecretCryptoService) {
        this.toolRepository = toolRepository;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
        this.toolSecretCryptoService = toolSecretCryptoService;
    }

    /**
     * 分页查询 Tool。
     *
     * @param request 查询条件
     * @return Tool 分页结果
     */
    public PageResponse<ToolPageItemResponse> pageTools(ToolPageQueryRequest request) {
        ToolPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = toolRepository.countTools(
                normalizedRequest.toolCode(),
                normalizedRequest.toolName(),
                normalizedRequest.toolType(),
                normalizedRequest.toolCategory(),
                normalizedRequest.status()
        );
        List<ToolPageItemResponse> records = toolRepository.pageTools(
                        normalizedRequest.toolCode(),
                        normalizedRequest.toolName(),
                        normalizedRequest.toolType(),
                        normalizedRequest.toolCategory(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new ToolPageItemResponse(
                        item.toolId(),
                        item.toolCode(),
                        item.toolName(),
                        item.toolType(),
                        item.toolCategory(),
                        item.providerType(),
                        item.transportType(),
                        item.permissionLevel(),
                        item.timeoutSeconds(),
                        item.status(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询 Tool 详情。
     *
     * @param toolId Tool ID
     * @return Tool 详情
     */
    public ToolDetailResponse getToolDetail(Long toolId) {
        return toToolDetailResponse(getToolRecord(toolId), false);
    }

    /**
     * 查询 Tool 运行时详情。
     *
     * @param toolId Tool ID
     * @return Tool 运行时详情
     */
    public ToolDetailResponse getToolRuntimeDetail(Long toolId) {
        return toToolDetailResponse(getToolRecord(toolId), true);
    }

    /**
     * 新增 Tool。
     *
     * @param request      新增请求
     * @param securityUser 当前登录用户
     * @return Tool 详情
     */
    @Transactional
    public ToolDetailResponse createTool(CreateToolRequest request, SecurityUser securityUser) {
        CreateToolRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        validateStatus(normalizedRequest.providerStatus());
        validateStatus(normalizedRequest.providerInstanceStatus());
        validateStatus(normalizedRequest.bindingStatus());
        validateDictFields(normalizedRequest.toolType(), normalizedRequest.toolCategory(), normalizedRequest.permissionLevel());
        validateJsonObject("输入参数 Schema", normalizedRequest.inputSchema());
        validateJsonObject("输出结果 Schema", normalizedRequest.outputSchema());
        validateJsonObject("Provider 配置 Schema", normalizedRequest.providerConfigSchema());
        validateJsonObject("认证配置", normalizedRequest.authConfig());
        validateJsonObject("默认请求头", normalizedRequest.headers());
        validateJsonObject("调用配置", normalizedRequest.invokeConfig());
        validateMcpTransportConfig(normalizedRequest.providerType(), normalizedRequest.transportType(),
                normalizedRequest.endpoint(), normalizedRequest.invokeConfig());
        validateJsonObject("参数映射配置", normalizedRequest.argumentMapping());
        validateJsonObject("输出映射配置", normalizedRequest.outputMapping());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        RuntimeCapabilityContract.validateSideEffect(
                normalizedRequest.sideEffectLevel(), normalizedRequest.metadata());
        validateProviderDictFields(normalizedRequest.providerType(),
                normalizedRequest.transportType(),
                normalizedRequest.authType(),
                normalizedRequest.operationType());
        ensureToolCodeUnique(normalizedRequest.toolCode(), null);
        Long toolId = toolRepository.insertTool(
                normalizedRequest,
                toJson(normalizedRequest.inputSchema()),
                toJson(normalizedRequest.outputSchema()),
                toJson(objectMapper.valueToTree(normalizedRequest.runtimeCapabilities())),
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        upsertBindingResources(
                toolId,
                normalizedRequest.providerCode(),
                normalizedRequest.providerName(),
                normalizedRequest.providerType(),
                normalizedRequest.transportType(),
                toJson(normalizedRequest.providerConfigSchema()),
                normalizedRequest.providerStatus(),
                normalizedRequest.providerInstanceCode(),
                normalizedRequest.providerInstanceName(),
                normalizedRequest.environmentCode(),
                normalizedRequest.endpoint(),
                normalizedRequest.authType(),
                toolSecretCryptoService.encryptJsonText(toJson(normalizedRequest.authConfig())),
                toolSecretCryptoService.encryptJsonText(toJson(normalizedRequest.headers())),
                normalizedRequest.providerTimeoutSeconds(),
                normalizedRequest.retryTimes(),
                normalizedRequest.providerInstanceStatus(),
                normalizedRequest.operationName(),
                normalizedRequest.operationType(),
                toJson(normalizedRequest.invokeConfig()),
                toJson(normalizedRequest.argumentMapping()),
                toJson(normalizedRequest.outputMapping()),
                normalizedRequest.bindingStatus(),
                securityUser.userId()
        );
        return getToolDetail(toolId);
    }

    /**
     * 编辑 Tool。
     *
     * @param toolId       Tool ID
     * @param request      编辑请求
     * @param securityUser 当前登录用户
     * @return Tool 详情
     */
    @Transactional
    public ToolDetailResponse updateTool(Long toolId, UpdateToolRequest request, SecurityUser securityUser) {
        UpdateToolRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        validateStatus(normalizedRequest.providerStatus());
        validateStatus(normalizedRequest.providerInstanceStatus());
        validateStatus(normalizedRequest.bindingStatus());
        validateDictFields(normalizedRequest.toolType(), normalizedRequest.toolCategory(), normalizedRequest.permissionLevel());
        validateJsonObject("输入参数 Schema", normalizedRequest.inputSchema());
        validateJsonObject("输出结果 Schema", normalizedRequest.outputSchema());
        validateJsonObject("Provider 配置 Schema", normalizedRequest.providerConfigSchema());
        validateJsonObject("认证配置", normalizedRequest.authConfig());
        validateJsonObject("默认请求头", normalizedRequest.headers());
        validateJsonObject("调用配置", normalizedRequest.invokeConfig());
        validateMcpTransportConfig(normalizedRequest.providerType(), normalizedRequest.transportType(),
                normalizedRequest.endpoint(), normalizedRequest.invokeConfig());
        validateJsonObject("参数映射配置", normalizedRequest.argumentMapping());
        validateJsonObject("输出映射配置", normalizedRequest.outputMapping());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        RuntimeCapabilityContract.validateSideEffect(
                normalizedRequest.sideEffectLevel(), normalizedRequest.metadata());
        validateProviderDictFields(normalizedRequest.providerType(),
                normalizedRequest.transportType(),
                normalizedRequest.authType(),
                normalizedRequest.operationType());
        ensureToolExists(toolId);
        ensureToolCodeUnique(normalizedRequest.toolCode(), toolId);
        int updated = toolRepository.updateTool(
                toolId,
                normalizedRequest,
                toJson(normalizedRequest.inputSchema()),
                toJson(normalizedRequest.outputSchema()),
                toJson(objectMapper.valueToTree(normalizedRequest.runtimeCapabilities())),
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("Tool 不存在");
        }
        upsertBindingResources(
                toolId,
                normalizedRequest.providerCode(),
                normalizedRequest.providerName(),
                normalizedRequest.providerType(),
                normalizedRequest.transportType(),
                toJson(normalizedRequest.providerConfigSchema()),
                normalizedRequest.providerStatus(),
                normalizedRequest.providerInstanceCode(),
                normalizedRequest.providerInstanceName(),
                normalizedRequest.environmentCode(),
                normalizedRequest.endpoint(),
                normalizedRequest.authType(),
                toolSecretCryptoService.encryptJsonText(toJson(normalizedRequest.authConfig())),
                toolSecretCryptoService.encryptJsonText(toJson(normalizedRequest.headers())),
                normalizedRequest.providerTimeoutSeconds(),
                normalizedRequest.retryTimes(),
                normalizedRequest.providerInstanceStatus(),
                normalizedRequest.operationName(),
                normalizedRequest.operationType(),
                toJson(normalizedRequest.invokeConfig()),
                toJson(normalizedRequest.argumentMapping()),
                toJson(normalizedRequest.outputMapping()),
                normalizedRequest.bindingStatus(),
                securityUser.userId()
        );
        return getToolDetail(toolId);
    }

    /**
     * 更新 Tool 状态。
     *
     * @param toolId       Tool ID
     * @param status       Tool 状态
     * @param securityUser 当前登录用户
     */
    @Transactional
    public void updateToolStatus(Long toolId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensureToolExists(toolId);
        int updated = toolRepository.updateToolStatus(toolId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Tool 不存在");
        }
    }

    private ToolPageQueryRequest normalizePageQueryRequest(ToolPageQueryRequest request) {
        return new ToolPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.toolCode()),
                normalizeNullableText(request.toolName()),
                normalizeNullableText(request.toolType()),
                normalizeNullableText(request.toolCategory()),
                normalizeNullableText(request.status())
        );
    }

    private CreateToolRequest normalizeCreateRequest(CreateToolRequest request) {
        return new CreateToolRequest(
                request.toolCode().trim(),
                request.toolName().trim(),
                request.toolType().trim(),
                request.toolCategory().trim(),
                RuntimeCapabilityContract.normalizeCapabilities(request.runtimeCapabilities()),
                request.sideEffectLevel().trim(),
                normalizeNullableText(request.description()),
                request.inputSchema(),
                request.outputSchema(),
                request.providerCode().trim(),
                request.providerName().trim(),
                request.providerType().trim(),
                request.transportType().trim(),
                normalizeObject(request.providerConfigSchema()),
                request.providerStatus().trim(),
                request.providerInstanceCode().trim(),
                request.providerInstanceName().trim(),
                request.environmentCode().trim(),
                normalizeNullableText(request.endpoint()),
                request.authType().trim(),
                normalizeObject(request.authConfig()),
                normalizeObject(request.headers()),
                request.providerTimeoutSeconds(),
                request.retryTimes(),
                request.providerInstanceStatus().trim(),
                request.operationName().trim(),
                request.operationType().trim(),
                normalizeObject(request.invokeConfig()),
                normalizeObject(request.argumentMapping()),
                normalizeObject(request.outputMapping()),
                request.bindingStatus().trim(),
                request.permissionLevel().trim(),
                request.timeoutSeconds(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateToolRequest normalizeUpdateRequest(UpdateToolRequest request) {
        return new UpdateToolRequest(
                request.toolCode().trim(),
                request.toolName().trim(),
                request.toolType().trim(),
                request.toolCategory().trim(),
                RuntimeCapabilityContract.normalizeCapabilities(request.runtimeCapabilities()),
                request.sideEffectLevel().trim(),
                normalizeNullableText(request.description()),
                request.inputSchema(),
                request.outputSchema(),
                request.providerCode().trim(),
                request.providerName().trim(),
                request.providerType().trim(),
                request.transportType().trim(),
                normalizeObject(request.providerConfigSchema()),
                request.providerStatus().trim(),
                request.providerInstanceCode().trim(),
                request.providerInstanceName().trim(),
                request.environmentCode().trim(),
                normalizeNullableText(request.endpoint()),
                request.authType().trim(),
                normalizeObject(request.authConfig()),
                normalizeObject(request.headers()),
                request.providerTimeoutSeconds(),
                request.retryTimes(),
                request.providerInstanceStatus().trim(),
                request.operationName().trim(),
                request.operationType().trim(),
                normalizeObject(request.invokeConfig()),
                normalizeObject(request.argumentMapping()),
                normalizeObject(request.outputMapping()),
                request.bindingStatus().trim(),
                request.permissionLevel().trim(),
                request.timeoutSeconds(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private JsonNode normalizeMetadata(JsonNode metadata) {
        return normalizeObject(metadata);
    }

    private JsonNode normalizeObject(JsonNode jsonNode) {
        return jsonNode == null ? objectMapper.createObjectNode() : jsonNode;
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("Tool 状态不合法");
        }
    }

    private void validateDictFields(String toolType, String toolCategory, String permissionLevel) {
        dictService.validateEnabledDictItem("tool_type", toolType, "Tool 类型");
        dictService.validateEnabledDictItem("tool_category", toolCategory, "Tool 分类");
        dictService.validateEnabledDictItem("permission_level", permissionLevel, "权限等级");
    }

    private void validateProviderDictFields(String providerType,
                                            String transportType,
                                            String authType,
                                            String operationType) {
        dictService.validateEnabledDictItem("tool_provider_type", providerType, "Tool Provider 类型");
        dictService.validateEnabledDictItem("tool_transport_type", transportType, "Tool Transport 类型");
        dictService.validateEnabledDictItem("tool_auth_type", authType, "Tool 认证类型");
        dictService.validateEnabledDictItem("tool_operation_type", operationType, "Tool 操作类型");
    }

    /**
     * 校验 MCP transport 的运行必需配置，避免发布后才发现无法启动或建立消息通道。
     */
    private void validateMcpTransportConfig(String providerType,
                                            String transportType,
                                            String endpoint,
                                            JsonNode invokeConfig) {
        if (!"mcp".equals(providerType)) {
            return;
        }
        if ("stdio".equals(transportType)) {
            String command = invokeConfig == null ? "" : invokeConfig.path("command").asText("").trim();
            if (command.isBlank()) {
                throw new BusinessException("MCP stdio 必须配置 invokeConfig.command");
            }
            JsonNode args = invokeConfig.path("args");
            if (!args.isMissingNode() && !args.isArray()) {
                throw new BusinessException("MCP stdio 的 invokeConfig.args 必须为数组");
            }
            JsonNode environment = invokeConfig.path("environment");
            if (!environment.isMissingNode() && !environment.isObject()) {
                throw new BusinessException("MCP stdio 的 invokeConfig.environment 必须为对象");
            }
            return;
        }
        if (("sse".equals(transportType) || "streamable_http".equals(transportType) || "http_post".equals(transportType))
                && (endpoint == null || endpoint.isBlank())) {
            throw new BusinessException("MCP " + transportType + " 必须配置 Endpoint");
        }
    }

    private void validateJsonObject(String fieldName, JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isObject()) {
            throw new BusinessException(fieldName + "必须为 JSON 对象");
        }
    }

    private void ensureToolCodeUnique(String toolCode, Long excludeToolId) {
        toolRepository.findToolIdByToolCode(toolCode).ifPresent(existingToolId -> {
            if (excludeToolId == null || !existingToolId.equals(excludeToolId)) {
                throw new BusinessException("Tool 编码已存在");
            }
        });
    }

    private void ensureToolExists(Long toolId) {
        getToolRecord(toolId);
    }

    private ToolDetailRecord getToolRecord(Long toolId) {
        return toolRepository.findToolDetailById(toolId)
                .orElseThrow(() -> new BusinessException("Tool 不存在"));
    }

    private ToolDetailResponse toToolDetailResponse(ToolDetailRecord record, boolean includeSensitiveConfig) {
        JsonNode decryptedAuthConfig = toolSecretCryptoService.decryptJsonText(record.authConfigJson());
        JsonNode decryptedHeaders = toolSecretCryptoService.decryptJsonText(record.headersJson());
        return new ToolDetailResponse(
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.toolType(),
                record.toolCategory(),
                parseStringList(record.runtimeCapabilitiesJson()),
                record.sideEffectLevel(),
                record.description(),
                parseJson(record.inputSchemaJson()),
                parseJson(record.outputSchemaJson()),
                record.providerCode(),
                record.providerName(),
                record.providerType(),
                record.transportType(),
                parseJson(record.providerConfigSchemaJson()),
                record.providerStatus(),
                record.providerInstanceCode(),
                record.providerInstanceName(),
                record.environmentCode(),
                record.endpoint(),
                record.authType(),
                includeSensitiveConfig ? decryptedAuthConfig : toolSecretCryptoService.maskJson(decryptedAuthConfig),
                includeSensitiveConfig ? decryptedHeaders : toolSecretCryptoService.maskJson(decryptedHeaders),
                record.providerTimeoutSeconds(),
                record.retryTimes(),
                record.providerInstanceStatus(),
                record.operationName(),
                record.operationType(),
                parseJson(record.invokeConfigJson()),
                parseJson(record.argumentMappingJson()),
                parseJson(record.outputMappingJson()),
                record.bindingStatus(),
                record.permissionLevel(),
                record.timeoutSeconds(),
                record.status(),
                parseJson(record.metadataJson()),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Tool JSON 数据格式不合法");
        }
    }

    private List<String> parseStringList(String json) {
        JsonNode node = parseJson(json);
        if (!node.isArray()) {
            throw new BusinessException("Tool 运行时能力数据格式不合法");
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private String toJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Tool JSON 数据格式不合法");
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void upsertBindingResources(Long toolId,
                                        String providerCode,
                                        String providerName,
                                        String providerType,
                                        String transportType,
                                        String providerConfigSchemaJson,
                                        String providerStatus,
                                        String providerInstanceCode,
                                        String providerInstanceName,
                                        String environmentCode,
                                        String endpoint,
                                        String authType,
                                        String authConfigJson,
                                        String headersJson,
                                        Integer providerTimeoutSeconds,
                                        Integer retryTimes,
                                        String providerInstanceStatus,
                                        String operationName,
                                        String operationType,
                                        String invokeConfigJson,
                                        String argumentMappingJson,
                                        String outputMappingJson,
                                        String bindingStatus,
                                        Long operatorId) {
        Long providerId = toolRepository.upsertToolProvider(
                providerCode,
                providerName,
                providerType,
                transportType,
                providerConfigSchemaJson,
                providerStatus,
                operatorId
        );
        Long providerInstanceId = toolRepository.upsertToolProviderInstance(
                providerId,
                providerInstanceCode,
                providerInstanceName,
                environmentCode,
                endpoint,
                authType,
                authConfigJson,
                headersJson,
                providerTimeoutSeconds,
                retryTimes,
                providerInstanceStatus,
                operatorId
        );
        toolRepository.upsertToolBinding(
                toolId,
                providerId,
                providerInstanceId,
                operationName,
                operationType,
                invokeConfigJson,
                argumentMappingJson,
                outputMappingJson,
                bindingStatus,
                operatorId
        );
    }
}
