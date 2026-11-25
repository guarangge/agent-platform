package com.zc.agent.core.modules.conversation.client;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.RegistryProperties;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册中心 Agent 查询客户端。
 */
@Component
public class RegistryAgentClient {

    private static final String WORKFLOW_STATUS_PUBLISHED = "published";

    private final RestClient registryRestClient;
    private final ObjectMapper objectMapper;
    private final RegistryProperties registryProperties;

    public RegistryAgentClient(RestClient registryRestClient,
                               ObjectMapper objectMapper,
                               RegistryProperties registryProperties) {
        this.registryRestClient = registryRestClient;
        this.objectMapper = objectMapper;
        this.registryProperties = registryProperties;
    }

    /**
     * 查询 Agent 快照。
     *
     * @param agentId        Agent ID
     * @param authorization  原始 Authorization 头
     * @return Agent 快照
     */
    public AgentSnapshot getEnabledAgentSnapshot(Long agentId, String authorization) {
        ApiEnvelope<RegistryAgentDetailResponse> response = registryRestClient.get()
                .uri("/agents/{agentId}", agentId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Agent 配置失败");
        }
        RegistryAgentDetailResponse detail = response.data();
        if (!"enabled".equals(detail.status())) {
            throw new BusinessException("当前 Agent 未启用，不能创建会话");
        }
        return new AgentSnapshot(
                detail.agentId(),
                detail.agentCode(),
                detail.agentName(),
                detail.defaultModel(),
                detail.defaultPromptId(),
                detail.defaultPromptName(),
                detail.decisionPromptId(),
                detail.decisionPromptName(),
                detail.plannerPromptId(),
                detail.plannerPromptName(),
                detail.status(),
                detail.metadata()
        );
    }

    /**
     * 查询 Agent 当前有效 Tool 列表。
     *
     * @param agentId        Agent ID
     * @param authorization  原始 Authorization 头
     * @return 有效 Tool 列表
     */
    public List<RegistryAgentTool> getEffectiveTools(Long agentId, String authorization) {
        ApiEnvelope<RegistryAgentToolsResponse> response = registryRestClient.get()
                .uri("/agents/{agentId}/tools/runtime", agentId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Agent Tool 配置失败");
        }
        RegistryAgentToolsResponse data = response.data();
        Map<Long, RegistryAgentTool> effectiveTools = new LinkedHashMap<>();
        if ("inherit_default".equals(data.toolScopeMode()) || "inherit_and_append".equals(data.toolScopeMode())) {
            data.availableTools().stream()
                    .filter(tool -> "enabled".equals(tool.status()) && "platform_default".equals(tool.toolCategory()))
                    .forEach(tool -> effectiveTools.put(tool.toolId(), toRegistryAgentTool(tool)));
        }
        if ("custom".equals(data.toolScopeMode()) || "inherit_and_append".equals(data.toolScopeMode())) {
            data.tools().stream()
                    .filter(tool -> "enabled".equals(tool.status()))
                    .forEach(tool -> effectiveTools.put(tool.toolId(), toRegistryAgentTool(tool)));
        }
        return new ArrayList<>(effectiveTools.values());
    }

    public RegistryAgentTool getRuntimeToolDetail(Long toolId, String authorization) {
        ApiEnvelope<RegistryAgentToolDetailResponse> response = registryRestClient.get()
                .uri("/tools/{toolId}/runtime", toolId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Tool 运行时配置失败");
        }
        return toRegistryAgentTool(response.data());
    }

    /**
     * 查询 Agent 当前有效 Workflow 列表。
     *
     * @param agentId        Agent ID
     * @param authorization  原始 Authorization 头
     * @return 有效 Workflow 列表
     */
    public List<RegistryAgentWorkflow> getAvailableWorkflows(Long agentId, String authorization) {
        ApiEnvelope<RegistryAgentWorkflowsResponse> response = registryRestClient.get()
                .uri("/agents/{agentId}/workflows", agentId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Agent Workflow 配置失败");
        }
        return response.data().workflows().stream()
                .filter(workflow -> WORKFLOW_STATUS_PUBLISHED.equals(workflow.status()))
                .map(this::toRegistryAgentWorkflow)
                .toList();
    }

    /**
     * 查询当前启用的 Agent 列表，作为 handoff 候选。
     *
     * @param authorization 原始 Authorization 头
     * @return 启用 Agent 列表
     */
    public List<ExecutionContext.DelegateAgent> getEnabledAgents(String authorization) {
        ApiEnvelope<PageEnvelope<RegistryAgentPageItemResponse>> response = registryRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/agents")
                        .queryParam("pageNum", 1)
                        .queryParam("pageSize", 100)
                        .queryParam("status", "enabled")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Agent 列表失败");
        }
        return response.data().records().stream()
                .map(item -> new ExecutionContext.DelegateAgent(
                        item.agentId(),
                        item.agentCode(),
                        item.agentName(),
                        item.description()
                ))
                .toList();
    }

    /**
     * 查询 Workflow 快照。
     *
     * @param workflowId      Workflow ID
     * @param authorization   原始 Authorization 头
     * @return Workflow 快照
     */
    public RegistryWorkflowSnapshot getWorkflowSnapshot(Long workflowId, String authorization) {
        ApiEnvelope<RegistryWorkflowDetailResponse> response = registryRestClient.get()
                .uri("/workflows/{workflowId}", workflowId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Workflow 配置失败");
        }
        RegistryWorkflowDetailResponse detail = response.data();
        return new RegistryWorkflowSnapshot(
                detail.workflowId(),
                detail.workflowCode(),
                detail.workflowName(),
                detail.workflowType(),
                detail.workflowCategory(),
                detail.status(),
                detail.definitionJson(),
                detail.metadata()
        );
    }

    /**
     * 查询 Prompt 快照。
     *
     * @param promptId       Prompt ID
     * @param authorization  原始 Authorization 头
     * @return Prompt 快照
     */
    public ExecutionContext.PromptContext getPromptSnapshot(Long promptId, String authorization) {
        ApiEnvelope<RegistryPromptDetailResponse> response = registryRestClient.get()
                .uri("/prompts/{promptId}", promptId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Prompt 配置失败");
        }
        RegistryPromptDetailResponse detail = response.data();
        return new ExecutionContext.PromptContext(
                detail.promptId(),
                detail.promptCode(),
                detail.promptName(),
                detail.promptType(),
                detail.promptCategory(),
                detail.content()
        );
    }

    /** 按稳定分流规则解析 Prompt 实验 Variant；未命中实验时返回空。 */
    public java.util.Optional<PromptExperimentSelection> resolvePromptExperiment(Long basePromptId,Long userId,
                                                                                  Long conversationId,Long agentId,
                                                                                  String stage,String authorization){
        ApiEnvelope<java.util.Map<String,Object>> response=registryRestClient.post().uri("/prompt-experiments/resolve")
                .header(HttpHeaders.AUTHORIZATION,authorization).contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("basePromptId",basePromptId,"userId",userId,"conversationId",conversationId,"agentId",agentId,"stage",stage))
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>(){});
        if(response==null||response.code()!=200||response.data()==null||!Boolean.TRUE.equals(response.data().get("matched")))return java.util.Optional.empty();
        var d=response.data();return java.util.Optional.of(new PromptExperimentSelection(number(d,"experiment_id"),String.valueOf(d.get("experiment_code")),number(d,"variant_id"),String.valueOf(d.get("variant_code")),basePromptId,number(d,"prompt_id"),String.valueOf(d.get("prompt_code")),String.valueOf(d.get("prompt_name")),String.valueOf(d.get("prompt_type")),String.valueOf(d.get("prompt_category")),String.valueOf(d.get("content")),stage));
    }

    private Long number(java.util.Map<String,Object> value,String key){Object v=value.get(key);return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}

    /** Prompt 实验运行时选中快照。 */
    public record PromptExperimentSelection(Long experimentId,String experimentCode,Long variantId,String variantCode,
                                             Long basePromptId,Long selectedPromptId,String promptCode,String promptName,
                                             String promptType,String promptCategory,String content,String stage){}

    /**
     * 查询模型快照。
     *
     * @param modelCode      模型编码
     * @param authorization  原始 Authorization 头
     * @return 模型快照
     */
    public ExecutionContext.ModelContext getModelSnapshot(String modelCode, String authorization) {
        ApiEnvelope<RegistryModelDetailResponse> response = registryRestClient.get()
                .uri("/models/code/{modelCode}/runtime", modelCode)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Registry-Service-Token", registryProperties.getServiceToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取模型配置失败");
        }
        RegistryModelDetailResponse detail = response.data();
        return new ExecutionContext.ModelContext(
                detail.modelCode(),
                detail.modelName(),
                detail.provider(),
                detail.modelType(),
                detail.baseUrl(),
                detail.apiKey(),
                detail.apiPath(),
                detail.modelIdentifier(),
                detail.temperature(),
                detail.maxTokens(),
                detail.contextWindow(),
                detail.reservedOutputTokens(),
                detail.reservedSystemTokens(),
                detail.compactionTriggerRatio(),
                detail.tokenizerFamily(),
                detail.timeoutSeconds(),
                detail.status(),
                detail.metadata(),
                null
        );
    }

    /**
     * 查询当前生效的 Guardrail 策略列表。
     *
     * @param authorization 原始 Authorization 头
     * @return 策略规则列表
     */
    public List<JsonNode> getEffectiveGuardrailPolicies(String authorization) {
        ApiEnvelope<List<RuntimeGuardrailPolicyResponse>> response = registryRestClient.get()
                .uri("/guardrail-policies/runtime/effective")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (response == null || response.data() == null || response.code() != 200) {
            throw new BusinessException("读取 Guardrail 策略失败");
        }
        return response.data().stream()
                .map(this::toPolicyRuleJson)
                .toList();
    }

    public List<RegistryAgentTool> attachGuardrailPolicies(List<RegistryAgentTool> tools, List<JsonNode> policyRules) {
        return tools.stream()
                .map(tool -> new RegistryAgentTool(
                        tool.toolId(),
                        tool.toolCode(),
                        tool.toolName(),
                        tool.toolType(),
                        tool.toolCategory(),
                        tool.runtimeCapabilities(),
                        tool.sideEffectLevel(),
                        tool.inputSchema(),
                        tool.outputSchema(),
                        tool.providerType(),
                        tool.transportType(),
                        tool.providerCode(),
                        tool.providerName(),
                        tool.providerInstanceCode(),
                        tool.providerInstanceName(),
                        tool.environmentCode(),
                        tool.endpoint(),
                        tool.authType(),
                        tool.authConfig(),
                        tool.headers(),
                        tool.providerTimeoutSeconds(),
                        tool.retryTimes(),
                        tool.operationName(),
                        tool.operationType(),
                        tool.invokeConfig(),
                        tool.argumentMapping(),
                        tool.outputMapping(),
                        tool.permissionLevel(),
                        tool.timeoutSeconds(),
                        tool.status(),
                        tool.description(),
                        appendPolicyRules(tool.metadata(), policyRules)
                ))
                .toList();
    }

    public List<RegistryAgentWorkflow> attachGuardrailPoliciesToWorkflows(List<RegistryAgentWorkflow> workflows,
                                                                          List<JsonNode> policyRules) {
        return workflows.stream()
                .map(workflow -> new RegistryAgentWorkflow(
                        workflow.workflowId(),
                        workflow.workflowCode(),
                        workflow.workflowName(),
                        workflow.workflowType(),
                        workflow.workflowCategory(),
                        workflow.status(),
                        workflow.description(),
                        appendPolicyRules(workflow.metadata(), policyRules)
                ))
                .toList();
    }

    /**
     * 将生效策略附加到 Agent 运行时元数据，供输出 Guardrail 等 Agent 级阶段使用。
     */
    public ExecutionContext.AgentContext attachGuardrailPoliciesToAgent(ExecutionContext.AgentContext agent,
                                                                         List<JsonNode> policyRules) {
        return new ExecutionContext.AgentContext(
                agent.agentId(), agent.agentCode(), agent.agentName(), agent.defaultModel(), agent.agentStatus(),
                appendPolicyRules(agent.metadata(), policyRules)
        );
    }

    private JsonNode appendPolicyRules(JsonNode metadata, List<JsonNode> policyRules) {
        ObjectNode root = metadata == null || metadata.isNull() || metadata.isMissingNode()
                ? objectMapper.createObjectNode()
                : metadata.deepCopy();
        ObjectNode policyCenter = root.with("policyCenter");
        ArrayNode rules = policyCenter.withArray("rules");
        for (JsonNode policyRule : policyRules) {
            rules.add(policyRule.deepCopy());
        }
        return root;
    }

    private JsonNode toPolicyRuleJson(RuntimeGuardrailPolicyResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("policyCode", response.policyCode());
        node.put("policyName", response.policyName());
        node.put("version", response.version());
        node.put("enabled", response.enabled());
        node.put("priority", response.priority());
        node.put("layer", response.layer());
        node.set("scope", objectMapper.valueToTree(response.scope()));
        node.set("rollout", objectMapper.valueToTree(response.rollout()));
        node.set("match", objectMapper.valueToTree(response.match()));
        node.set("action", objectMapper.valueToTree(response.action()));
        JsonNode metadata = response.metadata();
        node.set("metadata", metadata == null ? objectMapper.createObjectNode() : metadata.deepCopy());
        node.put("stage", metadata == null ? "input" : metadata.path("stage").asText("input"));
        node.set("output", metadata == null ? objectMapper.createObjectNode() : metadata.path("output").deepCopy());
        return node;
    }

    private RegistryAgentTool toRegistryAgentTool(RegistryAgentToolResponse response) {
        return toRegistryAgentTool(new RegistryAgentToolDetailResponse(
                response.toolId(),
                response.toolCode(),
                response.toolName(),
                response.toolType(),
                response.toolCategory(),
                response.runtimeCapabilities(),
                response.sideEffectLevel(),
                response.description(),
                response.inputSchema(),
                response.outputSchema(),
                response.providerCode(),
                response.providerName(),
                response.providerType(),
                response.transportType(),
                null,
                null,
                response.providerInstanceCode(),
                response.providerInstanceName(),
                response.environmentCode(),
                response.endpoint(),
                response.authType(),
                response.authConfig(),
                response.headers(),
                response.providerTimeoutSeconds(),
                response.retryTimes(),
                null,
                response.operationName(),
                response.operationType(),
                response.invokeConfig(),
                response.argumentMapping(),
                response.outputMapping(),
                null,
                response.permissionLevel(),
                response.timeoutSeconds(),
                response.status(),
                response.metadata(),
                null,
                null,
                null,
                null,
                null
        ));
    }

    private RegistryAgentTool toRegistryAgentTool(RegistryAgentToolDetailResponse response) {
        return new RegistryAgentTool(
                response.toolId(),
                response.toolCode(),
                response.toolName(),
                response.toolType(),
                response.toolCategory(),
                response.runtimeCapabilities(),
                response.sideEffectLevel(),
                response.inputSchema(),
                response.outputSchema(),
                response.providerType(),
                response.transportType(),
                response.providerCode(),
                response.providerName(),
                response.providerInstanceCode(),
                response.providerInstanceName(),
                response.environmentCode(),
                response.endpoint(),
                response.authType(),
                response.authConfig(),
                response.headers(),
                response.providerTimeoutSeconds(),
                response.retryTimes(),
                response.operationName(),
                response.operationType(),
                response.invokeConfig(),
                response.argumentMapping(),
                response.outputMapping(),
                response.permissionLevel(),
                response.timeoutSeconds(),
                response.status(),
                response.description(),
                response.metadata()
        );
    }

    private RegistryAgentWorkflow toRegistryAgentWorkflow(RegistryAgentWorkflowResponse response) {
        return new RegistryAgentWorkflow(
                response.workflowId(),
                response.workflowCode(),
                response.workflowName(),
                response.workflowType(),
                response.workflowCategory(),
                response.status(),
                response.description(),
                response.metadata()
        );
    }

    private record ApiEnvelope<T>(int code, String message, T data) {
    }

    private record PageEnvelope<T>(
            List<T> records,
            long total,
            int pageNum,
            int pageSize
    ) {
    }

    /**
     * 注册中心 Agent 详情响应。
     *
     * @param agentId            Agent ID
     * @param agentCode          Agent 编码
     * @param agentName          Agent 名称
     * @param defaultModel       默认模型
     * @param defaultPromptId    默认 Prompt ID
     * @param defaultPromptName  默认 Prompt 名称
     * @param decisionPromptId   决策 Prompt ID
     * @param decisionPromptName 决策 Prompt 名称
     * @param plannerPromptId    规划 Prompt ID
     * @param plannerPromptName  规划 Prompt 名称
     * @param status             状态
     * @param metadata           元数据
     */
    private record RegistryAgentDetailResponse(
            Long agentId,
            String agentCode,
            String agentName,
            String defaultModel,
            Long defaultPromptId,
            String defaultPromptName,
            Long decisionPromptId,
            String decisionPromptName,
            Long plannerPromptId,
            String plannerPromptName,
            String status,
            JsonNode metadata
    ) {
    }

    private record RegistryAgentPageItemResponse(
            Long agentId,
            String agentCode,
            String agentName,
            String description,
            String version,
            String defaultModel,
            String status,
            String createdAt
    ) {
    }

    private record RegistryAgentToolsResponse(
            Long agentId,
            String toolScopeMode,
            List<Long> selectedToolIds,
            List<RegistryAgentToolResponse> tools,
            List<RegistryAgentToolResponse> availableTools
    ) {
    }

    private record RegistryAgentToolDetailResponse(
            Long toolId,
            String toolCode,
            String toolName,
            String toolType,
            String toolCategory,
            List<String> runtimeCapabilities,
            String sideEffectLevel,
            String description,
            JsonNode inputSchema,
            JsonNode outputSchema,
            String providerCode,
            String providerName,
            String providerType,
            String transportType,
            JsonNode providerConfigSchema,
            String providerStatus,
            String providerInstanceCode,
            String providerInstanceName,
            String environmentCode,
            String endpoint,
            String authType,
            JsonNode authConfig,
            JsonNode headers,
            Integer providerTimeoutSeconds,
            Integer retryTimes,
            String providerInstanceStatus,
            String operationName,
            String operationType,
            JsonNode invokeConfig,
            JsonNode argumentMapping,
            JsonNode outputMapping,
            String bindingStatus,
            String permissionLevel,
            Integer timeoutSeconds,
            String status,
            JsonNode metadata,
            String remark,
            String createdAt,
            String updatedAt,
            Long createdBy,
            Long updatedBy
    ) {
    }

    private record RegistryAgentToolResponse(
            Long toolId,
            String toolCode,
            String toolName,
            String toolType,
            String toolCategory,
            List<String> runtimeCapabilities,
            String sideEffectLevel,
            JsonNode inputSchema,
            JsonNode outputSchema,
            String providerType,
            String transportType,
            String providerCode,
            String providerName,
            String providerInstanceCode,
            String providerInstanceName,
            String environmentCode,
            String endpoint,
            String authType,
            JsonNode authConfig,
            JsonNode headers,
            Integer providerTimeoutSeconds,
            Integer retryTimes,
            String operationName,
            String operationType,
            JsonNode invokeConfig,
            JsonNode argumentMapping,
            JsonNode outputMapping,
            String permissionLevel,
            Integer timeoutSeconds,
            String status,
            String description,
            JsonNode metadata
    ) {
    }

    private record RegistryAgentWorkflowsResponse(
            Long agentId,
            List<Long> selectedWorkflowIds,
            List<RegistryAgentWorkflowResponse> workflows,
            List<RegistryAgentWorkflowResponse> availableWorkflows
    ) {
    }

    private record RegistryAgentWorkflowResponse(
            Long workflowId,
            String workflowCode,
            String workflowName,
            String workflowType,
            String workflowCategory,
            String status,
            String description,
            JsonNode metadata
    ) {
    }

    private record RegistryWorkflowDetailResponse(
            Long workflowId,
            String workflowCode,
            String workflowName,
            String workflowType,
            String workflowCategory,
            String status,
            JsonNode definitionJson,
            JsonNode metadata
    ) {
    }

    private record RegistryPromptDetailResponse(
            Long promptId,
            String promptCode,
            String promptName,
            String promptType,
            String promptCategory,
            String content,
            String status
    ) {
    }

    private record RegistryModelDetailResponse(
            Long modelId,
            String modelCode,
            String modelName,
            String provider,
            String modelType,
            String baseUrl,
            String apiKey,
            String apiPath,
            String modelIdentifier,
            Double temperature,
            Integer maxTokens,
            Integer contextWindow,
            Integer reservedOutputTokens,
            Integer reservedSystemTokens,
            Double compactionTriggerRatio,
            String tokenizerFamily,
            Integer timeoutSeconds,
            String status,
            JsonNode metadata,
            String remark,
            String createdAt,
            String updatedAt
    ) {
    }

    private record RuntimeGuardrailPolicyResponse(
            String policyCode,
            String policyName,
            Integer version,
            Boolean enabled,
            Integer priority,
            String layer,
            Map<String, Object> scope,
            Map<String, Object> rollout,
            Map<String, Object> match,
            Map<String, Object> action,
            JsonNode metadata
    ) {
    }
}
