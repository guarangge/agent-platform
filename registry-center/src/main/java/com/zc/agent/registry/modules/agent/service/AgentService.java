package com.zc.agent.registry.modules.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.common.runtime.RuntimeExecutionModeContract;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.agent.dto.AgentPageQueryRequest;
import com.zc.agent.registry.modules.agent.dto.CreateAgentRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentToolsRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentWorkflowsRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentRequest;
import com.zc.agent.registry.modules.agent.model.AgentDetailRecord;
import com.zc.agent.registry.modules.agent.model.AgentToolRecord;
import com.zc.agent.registry.modules.agent.model.AgentWorkflowRecord;
import com.zc.agent.registry.modules.agent.repository.AgentRepository;
import com.zc.agent.registry.modules.model.repository.ModelRepository;
import com.zc.agent.registry.modules.prompt.repository.PromptRepository;
import com.zc.agent.registry.modules.tool.service.ToolSecretCryptoService;
import com.zc.agent.registry.modules.tool.service.ToolCapabilityPackService;
import com.zc.agent.registry.modules.agent.vo.AgentDetailResponse;
import com.zc.agent.registry.modules.agent.vo.AgentPageItemResponse;
import com.zc.agent.registry.modules.agent.vo.AgentToolResponse;
import com.zc.agent.registry.modules.agent.vo.AgentToolsResponse;
import com.zc.agent.registry.modules.agent.vo.AgentWorkflowResponse;
import com.zc.agent.registry.modules.agent.vo.AgentWorkflowsResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 服务。
 */
@Service
public class AgentService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final String TOOL_SCOPE_MODE_CUSTOM = "custom";
    private static final String TOOL_SCOPE_MODE_INHERIT_DEFAULT = "inherit_default";
    private static final String TOOL_SCOPE_MODE_INHERIT_AND_APPEND = "inherit_and_append";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_DRAFT, STATUS_ENABLED, STATUS_DISABLED);
    private static final List<String> ALLOWED_TOOL_SCOPE_MODE = List.of(
            TOOL_SCOPE_MODE_CUSTOM,
            TOOL_SCOPE_MODE_INHERIT_DEFAULT,
            TOOL_SCOPE_MODE_INHERIT_AND_APPEND
    );

    private final AgentRepository agentRepository;
    private final DictService dictService;
    private final ModelRepository modelRepository;
    private final PromptRepository promptRepository;
    private final ObjectMapper objectMapper;
    private final ToolSecretCryptoService toolSecretCryptoService;
    private final AgentReleaseGateClient agentReleaseGateClient;
    private final ToolCapabilityPackService toolCapabilityPackService;
    public AgentService(AgentRepository agentRepository,
                        DictService dictService,
                        ModelRepository modelRepository,
                        PromptRepository promptRepository,
                        ObjectMapper objectMapper,
                        ToolSecretCryptoService toolSecretCryptoService,
                        AgentReleaseGateClient agentReleaseGateClient,
                        ToolCapabilityPackService toolCapabilityPackService) {
        this.agentRepository = agentRepository;
        this.dictService = dictService;
        this.modelRepository = modelRepository;
        this.promptRepository = promptRepository;
        this.objectMapper = objectMapper;
        this.toolSecretCryptoService = toolSecretCryptoService;
        this.agentReleaseGateClient = agentReleaseGateClient;
        this.toolCapabilityPackService = toolCapabilityPackService;
    }

    /**
     * 分页查询 Agent。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResponse<AgentPageItemResponse> pageAgents(AgentPageQueryRequest request) {
        AgentPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = agentRepository.countAgents(
                normalizedRequest.agentCode(),
                normalizedRequest.agentName(),
                normalizedRequest.status(),
                normalizedRequest.version()
        );
        List<AgentPageItemResponse> records = agentRepository.pageAgents(
                        normalizedRequest.agentCode(),
                        normalizedRequest.agentName(),
                        normalizedRequest.status(),
                        normalizedRequest.version(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new AgentPageItemResponse(
                        item.agentId(),
                        item.agentCode(),
                        item.agentName(),
                        item.description(),
                        item.version(),
                        item.defaultModel(),
                        item.status(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询 Agent 详情。
     *
     * @param agentId Agent ID
     * @return Agent 详情
     */
    public AgentDetailResponse getAgentDetail(Long agentId) {
        return toAgentDetailResponse(getAgentRecord(agentId));
    }

    /**
     * 查询 Agent 可用 Tool。
     *
     * @param agentId Agent ID
     * @return Tool 范围结果
     */
    public AgentToolsResponse getAgentTools(Long agentId) {
        AgentDetailRecord agentRecord = getAgentRecord(agentId);
        List<AgentToolRecord> selectedToolRecords = agentRepository.findAgentToolsByAgentId(agentId);
        List<AgentToolRecord> availableToolRecords = agentRepository.findAvailableTools();
        return toAgentToolsResponse(agentRecord, selectedToolRecords, availableToolRecords, false);
    }

    /**
     * 查询 Agent 运行时 Tool。
     *
     * @param agentId Agent ID
     * @return Tool 范围结果
     */
    public AgentToolsResponse getAgentRuntimeTools(Long agentId) {
        AgentDetailRecord agentRecord = getAgentRecord(agentId);
        List<AgentToolRecord> selectedToolRecords = agentRepository.findAgentToolsByAgentId(agentId);
        List<AgentToolRecord> availableToolRecords = agentRepository.findAvailableTools();
        List<AgentToolRecord> scopeToolRecords = resolveEffectiveTools(
                agentRecord.toolScopeMode(), selectedToolRecords, availableToolRecords);
        Set<Long> capabilityPackToolIds = new LinkedHashSet<>(toolCapabilityPackService.effectiveToolIds(agentId));
        LinkedHashMap<Long, AgentToolRecord> effective = new LinkedHashMap<>();
        scopeToolRecords.forEach(item -> effective.put(item.toolId(), item));
        availableToolRecords.stream().filter(item -> capabilityPackToolIds.contains(item.toolId()))
                .forEach(item -> effective.put(item.toolId(), item));
        List<AgentToolRecord> effectiveToolRecords = List.copyOf(effective.values());
        return toAgentToolsResponse(agentRecord, effectiveToolRecords, availableToolRecords, true);
    }

    /**
     * 按 Tool 范围模式计算运行时有效集合，配置查询仍保留显式绑定集合。
     */
    private List<AgentToolRecord> resolveEffectiveTools(String scopeMode,
                                                        List<AgentToolRecord> selectedTools,
                                                        List<AgentToolRecord> availableTools) {
        if (TOOL_SCOPE_MODE_CUSTOM.equals(scopeMode)) {
            return selectedTools;
        }
        List<AgentToolRecord> defaults = availableTools.stream()
                .filter(item -> "platform_default".equals(item.toolCategory()))
                .filter(item -> STATUS_ENABLED.equals(item.status()))
                .toList();
        if (TOOL_SCOPE_MODE_INHERIT_DEFAULT.equals(scopeMode)) {
            return defaults;
        }
        LinkedHashMap<Long, AgentToolRecord> merged = new LinkedHashMap<>();
        defaults.forEach(item -> merged.put(item.toolId(), item));
        selectedTools.forEach(item -> merged.put(item.toolId(), item));
        return List.copyOf(merged.values());
    }

    /**
     * 查询 Agent 可用 Workflow。
     *
     * @param agentId Agent ID
     * @return Workflow 范围结果
     */
    public AgentWorkflowsResponse getAgentWorkflows(Long agentId) {
        AgentDetailRecord agentRecord = getAgentRecord(agentId);
        List<AgentWorkflowRecord> selectedWorkflowRecords = agentRepository.findAgentWorkflowsByAgentId(agentId);
        List<AgentWorkflowRecord> availableWorkflowRecords = agentRepository.findAvailableWorkflows();
        return toAgentWorkflowsResponse(agentRecord, selectedWorkflowRecords, availableWorkflowRecords);
    }

    /**
     * 新增 Agent。
     *
     * @param request      新增请求
     * @param securityUser 当前登录用户
     * @return Agent 详情
     */
    @Transactional
    public AgentDetailResponse createAgent(CreateAgentRequest request, SecurityUser securityUser) {
        CreateAgentRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        validateToolScopeMode(normalizedRequest.toolScopeMode());
        dictService.validateEnabledDictItem("agent_tool_scope_mode", normalizedRequest.toolScopeMode(), "Tool 范围模式");
        validateDefaultModel(normalizedRequest.defaultModel());
        validatePrompt(normalizedRequest.defaultPromptId(), "默认 Prompt");
        validatePrompt(normalizedRequest.decisionPromptId(), "决策 Prompt");
        validatePrompt(normalizedRequest.plannerPromptId(), "规划 Prompt");
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        RuntimeExecutionModeContract.validate(normalizedRequest.metadata());
        ensureAgentCodeUnique(normalizedRequest.agentCode(), null);
        Long agentId = agentRepository.insertAgent(normalizedRequest, securityUser.userId());
        if (STATUS_ENABLED.equals(normalizedRequest.status())) {
            agentReleaseGateClient.verify(agentId, normalizedRequest.metadata());
        }
        return getAgentDetail(agentId);
    }

    /**
     * 编辑 Agent。
     *
     * @param agentId       Agent ID
     * @param request       编辑请求
     * @param securityUser  当前登录用户
     * @return Agent 详情
     */
    @Transactional
    public AgentDetailResponse updateAgent(Long agentId, UpdateAgentRequest request, SecurityUser securityUser) {
        UpdateAgentRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        validateToolScopeMode(normalizedRequest.toolScopeMode());
        dictService.validateEnabledDictItem("agent_tool_scope_mode", normalizedRequest.toolScopeMode(), "Tool 范围模式");
        validateDefaultModel(normalizedRequest.defaultModel());
        validatePrompt(normalizedRequest.defaultPromptId(), "默认 Prompt");
        validatePrompt(normalizedRequest.decisionPromptId(), "决策 Prompt");
        validatePrompt(normalizedRequest.plannerPromptId(), "规划 Prompt");
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        RuntimeExecutionModeContract.validate(normalizedRequest.metadata());
        ensureAgentExists(agentId);
        ensureAgentCodeUnique(normalizedRequest.agentCode(), agentId);
        if (STATUS_ENABLED.equals(normalizedRequest.status())) {
            agentReleaseGateClient.verify(agentId, normalizedRequest.metadata());
        }
        int updated = agentRepository.updateAgent(agentId, normalizedRequest, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Agent 不存在");
        }
        return getAgentDetail(agentId);
    }

    /**
     * 覆盖更新 Agent Tool 范围。
     *
     * @param agentId       Agent ID
     * @param request       更新请求
     * @param securityUser  当前登录用户
     * @return 最新 Tool 范围结果
     */
    @Transactional
    public AgentToolsResponse updateAgentTools(Long agentId,
                                               UpdateAgentToolsRequest request,
                                               SecurityUser securityUser) {
        UpdateAgentToolsRequest normalizedRequest = normalizeUpdateAgentToolsRequest(request);
        validateToolScopeMode(normalizedRequest.toolScopeMode());
        dictService.validateEnabledDictItem("agent_tool_scope_mode", normalizedRequest.toolScopeMode(), "Tool 范围模式");
        int updated = agentRepository.updateAgentToolScopeMode(agentId, normalizedRequest.toolScopeMode(), securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Agent 不存在");
        }

        List<Long> currentToolIds = agentRepository.findBoundToolIdsByAgentId(agentId);
        if (TOOL_SCOPE_MODE_INHERIT_DEFAULT.equals(normalizedRequest.toolScopeMode())) {
            if (!currentToolIds.isEmpty()) {
                agentRepository.softDeleteAgentTools(agentId, currentToolIds, securityUser.userId());
            }
            return getAgentTools(agentId);
        }

        List<Long> targetToolIds = normalizeToolIds(normalizedRequest.toolIds());
        validateToolIdsExist(targetToolIds);

        Set<Long> currentToolIdSet = new LinkedHashSet<>(currentToolIds);
        List<Long> toolIdsToAdd = targetToolIds.stream()
                .filter(toolId -> !currentToolIdSet.contains(toolId))
                .toList();
        List<Long> toolIdsToRemove = currentToolIdSet.stream()
                .filter(toolId -> !targetToolIds.contains(toolId))
                .toList();

        if (!toolIdsToRemove.isEmpty()) {
            agentRepository.softDeleteAgentTools(agentId, toolIdsToRemove, securityUser.userId());
        }
        if (!toolIdsToAdd.isEmpty()) {
            agentRepository.insertAgentTools(agentId, toolIdsToAdd, securityUser.userId());
        }
        return getAgentTools(agentId);
    }

    /**
     * 覆盖更新 Agent Workflow 范围。
     *
     * @param agentId       Agent ID
     * @param request       更新请求
     * @param securityUser  当前登录用户
     * @return 最新 Workflow 范围结果
     */
    @Transactional
    public AgentWorkflowsResponse updateAgentWorkflows(Long agentId,
                                                       UpdateAgentWorkflowsRequest request,
                                                       SecurityUser securityUser) {
        ensureAgentExists(agentId);
        List<Long> targetWorkflowIds = normalizeWorkflowIds(request.workflowIds());
        validateWorkflowIdsExist(targetWorkflowIds);

        List<Long> currentWorkflowIds = agentRepository.findBoundWorkflowIdsByAgentId(agentId);
        Set<Long> currentWorkflowIdSet = new LinkedHashSet<>(currentWorkflowIds);
        List<Long> workflowIdsToAdd = targetWorkflowIds.stream()
                .filter(workflowId -> !currentWorkflowIdSet.contains(workflowId))
                .toList();
        List<Long> workflowIdsToRemove = currentWorkflowIdSet.stream()
                .filter(workflowId -> !targetWorkflowIds.contains(workflowId))
                .toList();

        if (!workflowIdsToRemove.isEmpty()) {
            agentRepository.softDeleteAgentWorkflows(agentId, workflowIdsToRemove, securityUser.userId());
        }
        if (!workflowIdsToAdd.isEmpty()) {
            agentRepository.insertAgentWorkflows(agentId, workflowIdsToAdd, securityUser.userId());
        }
        return getAgentWorkflows(agentId);
    }

    /**
     * 更新 Agent 状态。
     *
     * @param agentId       Agent ID
     * @param status        状态
     * @param securityUser  当前登录用户
     */
    @Transactional
    public void updateAgentStatus(Long agentId, String status, SecurityUser securityUser) {
        validateStatus(status);
        AgentDetailRecord agent = getAgentRecord(agentId);
        if (STATUS_ENABLED.equals(status)) {
            try {
                agentReleaseGateClient.verify(agentId, objectMapper.readTree(agent.metadataJsonText()));
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException("Agent releaseGate 配置不是合法 JSON");
            }
        }
        int updated = agentRepository.updateAgentStatus(agentId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Agent 不存在");
        }
    }

    private AgentPageQueryRequest normalizePageQueryRequest(AgentPageQueryRequest request) {
        return new AgentPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.agentCode()),
                normalizeNullableText(request.agentName()),
                normalizeNullableText(request.status()),
                normalizeNullableText(request.version())
        );
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("Agent 状态不合法");
        }
    }

    private void validateToolScopeMode(String toolScopeMode) {
        if (!ALLOWED_TOOL_SCOPE_MODE.contains(toolScopeMode)) {
            throw new BusinessException("Tool 范围模式不合法");
        }
    }

    private void ensureAgentCodeUnique(String agentCode, Long excludeAgentId) {
        agentRepository.findAgentIdByAgentCode(agentCode.trim()).ifPresent(existingAgentId -> {
            if (excludeAgentId == null || !existingAgentId.equals(excludeAgentId)) {
                throw new BusinessException("Agent 编码已存在");
            }
        });
    }

    private void ensureAgentExists(Long agentId) {
        getAgentRecord(agentId);
    }

    private AgentDetailRecord getAgentRecord(Long agentId) {
        return agentRepository.findAgentDetailById(agentId)
                .orElseThrow(() -> new BusinessException("Agent 不存在"));
    }

    private CreateAgentRequest normalizeCreateRequest(CreateAgentRequest request) {
        return new CreateAgentRequest(
                request.agentCode().trim(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.version().trim(),
                normalizeNullableText(request.defaultModel()),
                request.defaultPromptId(),
                request.decisionPromptId(),
                request.plannerPromptId(),
                request.toolScopeMode().trim(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateAgentRequest normalizeUpdateRequest(UpdateAgentRequest request) {
        return new UpdateAgentRequest(
                request.agentCode().trim(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.version().trim(),
                normalizeNullableText(request.defaultModel()),
                request.defaultPromptId(),
                request.decisionPromptId(),
                request.plannerPromptId(),
                request.toolScopeMode().trim(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JsonNode normalizeMetadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        return metadata;
    }

    private void validateJsonObject(String label, JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return;
        }
        if (!jsonNode.isObject()) {
            throw new BusinessException(label + "必须是 JSON object");
        }
    }

    private UpdateAgentToolsRequest normalizeUpdateAgentToolsRequest(UpdateAgentToolsRequest request) {
        return new UpdateAgentToolsRequest(
                request.toolScopeMode().trim(),
                request.toolIds()
        );
    }

    private List<Long> normalizeToolIds(List<Long> toolIds) {
        boolean containsInvalidToolId = toolIds.stream()
                .anyMatch(toolId -> toolId == null || toolId <= 0);
        if (containsInvalidToolId) {
            throw new BusinessException("Tool ID 必须为正整数");
        }
        return toolIds.stream()
                .distinct()
                .toList();
    }

    private List<Long> normalizeWorkflowIds(List<Long> workflowIds) {
        boolean containsInvalidWorkflowId = workflowIds.stream()
                .anyMatch(workflowId -> workflowId == null || workflowId <= 0);
        if (containsInvalidWorkflowId) {
            throw new BusinessException("Workflow ID 必须为正整数");
        }
        return workflowIds.stream()
                .distinct()
                .toList();
    }

    private void validateToolIdsExist(List<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return;
        }
        Set<Long> existingToolIds = new LinkedHashSet<>(agentRepository.findExistingToolIds(toolIds));
        List<Long> missingToolIds = toolIds.stream()
                .filter(toolId -> !existingToolIds.contains(toolId))
                .toList();
        if (!missingToolIds.isEmpty()) {
            throw new BusinessException("Tool 不存在: " + missingToolIds);
        }
    }

    private void validateWorkflowIdsExist(List<Long> workflowIds) {
        if (workflowIds.isEmpty()) {
            return;
        }
        Set<Long> existingWorkflowIds = new LinkedHashSet<>(agentRepository.findExistingWorkflowIds(workflowIds));
        List<Long> missingWorkflowIds = workflowIds.stream()
                .filter(workflowId -> !existingWorkflowIds.contains(workflowId))
                .toList();
        if (!missingWorkflowIds.isEmpty()) {
            throw new BusinessException("Workflow 不存在: " + missingWorkflowIds);
        }
    }

    private void validatePrompt(Long promptId, String label) {
        if (promptId != null) {
            promptRepository.findPromptDetailById(promptId)
                    .orElseThrow(() -> new BusinessException(label + "不存在"));
        }
    }

    private void validateDefaultModel(String defaultModel) {
        if (defaultModel != null && !modelRepository.existsEnabledModelCode(defaultModel)) {
            throw new BusinessException("默认模型不存在或未启用");
        }
    }

    private AgentDetailResponse toAgentDetailResponse(AgentDetailRecord record) {
        return new AgentDetailResponse(
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.description(),
                record.version(),
                record.defaultModel(),
                record.defaultPromptId(),
                record.defaultPromptName(),
                record.decisionPromptId(),
                record.decisionPromptName(),
                record.plannerPromptId(),
                record.plannerPromptName(),
                record.toolScopeMode(),
                record.status(),
                parseMetadata(record.metadataJsonText()),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private AgentToolsResponse toAgentToolsResponse(AgentDetailRecord agentRecord,
                                                    List<AgentToolRecord> toolRecords,
                                                    List<AgentToolRecord> availableToolRecords,
                                                    boolean includeSensitiveConfig) {
        List<AgentToolResponse> tools = toolRecords.stream()
                .map(item -> toAgentToolResponse(item, includeSensitiveConfig))
                .toList();
        List<Long> selectedToolIds = toolRecords.stream()
                .map(AgentToolRecord::toolId)
                .toList();
        List<AgentToolResponse> availableTools = availableToolRecords.stream()
                .map(item -> toAgentToolResponse(item, includeSensitiveConfig))
                .toList();
        return new AgentToolsResponse(agentRecord.agentId(), agentRecord.toolScopeMode(), selectedToolIds, tools, availableTools);
    }

    private AgentWorkflowsResponse toAgentWorkflowsResponse(AgentDetailRecord agentRecord,
                                                            List<AgentWorkflowRecord> workflowRecords,
                                                            List<AgentWorkflowRecord> availableWorkflowRecords) {
        List<AgentWorkflowResponse> workflows = workflowRecords.stream()
                .map(this::toAgentWorkflowResponse)
                .toList();
        List<Long> selectedWorkflowIds = workflowRecords.stream()
                .map(AgentWorkflowRecord::workflowId)
                .toList();
        List<AgentWorkflowResponse> availableWorkflows = availableWorkflowRecords.stream()
                .map(this::toAgentWorkflowResponse)
                .toList();
        return new AgentWorkflowsResponse(agentRecord.agentId(), selectedWorkflowIds, workflows, availableWorkflows);
    }

    private AgentToolResponse toAgentToolResponse(AgentToolRecord record, boolean includeSensitiveConfig) {
        JsonNode decryptedAuthConfig = toolSecretCryptoService.decryptJsonText(record.authConfigJsonText());
        JsonNode decryptedHeaders = toolSecretCryptoService.decryptJsonText(record.headersJsonText());
        return new AgentToolResponse(
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.toolType(),
                record.toolCategory(),
                parseStringList(record.runtimeCapabilitiesJsonText()),
                record.sideEffectLevel(),
                parseMetadata(record.inputSchemaJsonText()),
                parseMetadata(record.outputSchemaJsonText()),
                record.providerType(),
                record.transportType(),
                record.providerCode(),
                record.providerName(),
                record.providerInstanceCode(),
                record.providerInstanceName(),
                record.environmentCode(),
                record.endpoint(),
                record.authType(),
                includeSensitiveConfig ? decryptedAuthConfig : toolSecretCryptoService.maskJson(decryptedAuthConfig),
                includeSensitiveConfig ? decryptedHeaders : toolSecretCryptoService.maskJson(decryptedHeaders),
                record.providerTimeoutSeconds(),
                record.retryTimes(),
                record.operationName(),
                record.operationType(),
                parseMetadata(record.invokeConfigJsonText()),
                parseMetadata(record.argumentMappingJsonText()),
                parseMetadata(record.outputMappingJsonText()),
                record.permissionLevel(),
                record.timeoutSeconds(),
                record.status(),
                record.description(),
                parseMetadata(record.metadataJsonText()),
                record.boundAt()
        );
    }

    private List<String> parseStringList(String json) {
        JsonNode node = parseMetadata(json);
        if (!node.isArray()) {
            throw new BusinessException("Tool 运行时能力数据格式不合法");
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private AgentWorkflowResponse toAgentWorkflowResponse(AgentWorkflowRecord record) {
        return new AgentWorkflowResponse(
                record.workflowId(),
                record.workflowCode(),
                record.workflowName(),
                record.workflowType(),
                record.workflowCategory(),
                record.status(),
                record.description(),
                parseMetadata(record.metadataJsonText()),
                record.boundAt()
        );
    }

    private JsonNode parseMetadata(String metadataJsonText) {
        try {
            return objectMapper.readTree(metadataJsonText == null ? "{}" : metadataJsonText);
        } catch (Exception exception) {
            throw new BusinessException("资源 metadata 解析失败");
        }
    }
}
