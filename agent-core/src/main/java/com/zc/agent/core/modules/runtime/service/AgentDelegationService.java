package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 多 Agent handoff / delegation 服务。
 * 当前最小能力先支持把子任务委派给目标 Agent 生成独立结论，并将结果回写到父任务步骤。
 */
@Service
public class AgentDelegationService {

    private final RegistryAgentClient registryAgentClient;
    private final ConversationRepository conversationRepository;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final TaskTraceService taskTraceService;
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionService contextCompactionService;
    private final ObjectMapper objectMapper;
    private final CostGovernanceService costGovernanceService;
    private final DelegationGovernanceService delegationGovernanceService;

    public AgentDelegationService(RegistryAgentClient registryAgentClient,
                                  ConversationRepository conversationRepository,
                                  RuntimeLlmGatewayService runtimeLlmGatewayService,
                                  TaskTraceService taskTraceService,
                                  PromptContextBudgetService promptContextBudgetService,
                                  ContextCompactionService contextCompactionService,
                                  ObjectMapper objectMapper,
                                  CostGovernanceService costGovernanceService,
                                  DelegationGovernanceService delegationGovernanceService) {
        this.registryAgentClient = registryAgentClient;
        this.conversationRepository = conversationRepository;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.taskTraceService = taskTraceService;
        this.promptContextBudgetService = promptContextBudgetService;
        this.contextCompactionService = contextCompactionService;
        this.objectMapper = objectMapper;
        this.costGovernanceService = costGovernanceService;
        this.delegationGovernanceService = delegationGovernanceService;
    }

    /**
     * 执行 handoff 步骤。
     *
     * @param executionContext    父执行上下文
     * @param step                handoff 步骤
     * @param previousStepResults 前序步骤结果
     * @param authorization       原始授权头
     * @return 委派执行结果
     */
    public DelegationExecutionResult execute(ExecutionContext executionContext,
                                             ExecutionPlan.ExecutionPlanStep step,
                                             List<RuntimeStepResultContext> previousStepResults,
                                             String authorization,
                                             Long parentTaskId,
                                             Long parentTaskStepId,
                                             Long operatorId) {
        DelegationGovernanceService.Selection governance = delegationGovernanceService.selectAndValidate(
                executionContext, step.inputs(), parentTaskId);
        ExecutionContext.DelegateAgent delegateAgent = governance.delegate();
        Long delegateAgentId = delegateAgent.agentId();
        costGovernanceService.assertAllowed("delegation", delegateAgent.agentCode(), 0, 0, java.math.BigDecimal.ONE);
        long meteringStartedAt = System.nanoTime();
        AgentSnapshot delegateSnapshot = registryAgentClient.getEnabledAgentSnapshot(delegateAgentId, authorization);
        ExecutionContext.ModelContext delegateModel = withDelegationTimeout(
                resolveDelegateModel(delegateSnapshot, authorization), governance.timeoutSeconds(),
                executionContext.modelContext() == null ? null : executionContext.modelContext().callBudget());
        if (delegateModel == null) {
            throw new BusinessException("目标 Agent 未配置可用模型，无法执行 handoff: " + delegateAgent.agentName());
        }
        ExecutionContext.PromptContext delegatePrompt = resolveDelegatePrompt(delegateSnapshot, authorization);
        String delegateInstruction = resolveDelegateInstruction(step, executionContext);
        TaskRecord parentTaskRecord = conversationRepository.findTaskById(parentTaskId)
                .orElseThrow(() -> new BusinessException("父任务不存在: " + parentTaskId));
        Long rootTaskId = parentTaskRecord.rootTaskId() == null ? parentTaskRecord.taskId() : parentTaskRecord.rootTaskId();
        String childTaskCode = nextTaskCode();
        String childTraceId = nextTraceId();
        Long childTaskId = conversationRepository.insertTask(
                childTaskCode,
                childTraceId,
                executionContext.conversationContext().conversationId(),
                executionContext.messageContext().userMessageId(),
                delegateAgent.agentId(),
                delegateSnapshot.defaultPromptId(),
                delegateSnapshot.defaultPromptName(),
                parentTaskId,
                rootTaskId,
                parentTaskStepId,
                "delegated",
                operatorId
        );
        conversationRepository.updateTaskExecutionType(childTaskId, "delegated_agent", operatorId);
        conversationRepository.updateTaskExecutionChain(
                childTaskId,
                toJsonText(List.of(Map.of(
                        "resourceType", "delegated_agent",
                        "resourceId", delegateAgent.agentId(),
                        "resourceCode", delegateAgent.agentCode(),
                        "resourceName", delegateAgent.agentName()
                ))),
                operatorId
        );
        String systemPrompt = buildSystemPrompt(delegateAgent, delegatePrompt);
        String userPrompt = buildUserPrompt(executionContext, step, delegateInstruction, previousStepResults, delegateAgentId, authorization);
        String delegationInputSnapshot = toJsonText(Map.of(
                "parentTaskId", parentTaskId,
                "parentTaskStepId", parentTaskStepId,
                "delegateAgentId", delegateAgent.agentId(),
                "delegateAgentCode", delegateAgent.agentCode(),
                "delegateAgentName", delegateAgent.agentName(),
                "delegateInstruction", delegateInstruction,
                "userPrompt", userPrompt
        ));
        Long delegationId = conversationRepository.insertTaskDelegation(
                parentTaskId,
                parentTaskStepId,
                childTaskId,
                executionContext.conversationContext().conversationId(),
                delegateAgent.agentId(),
                delegateAgent.agentCode(),
                delegateAgent.agentName(),
                delegateInstruction,
                childTraceId,
                delegationInputSnapshot,
                operatorId
        );
        delegationGovernanceService.record(delegationId, governance);
        Long childTaskStepId = conversationRepository.insertTaskStep(
                childTaskId,
                1,
                "handoff",
                step.stepName(),
                delegationInputSnapshot,
                operatorId
        );
        LocalDateTime startedAt = LocalDateTime.now();
        taskTraceService.recordSpan(
                childTaskId,
                childTraceId,
                null,
                "context_build",
                "构建委派子任务上下文",
                "success",
                Map.of("parentTaskId", parentTaskId, "parentTaskStepId", parentTaskStepId),
                Map.of("delegateAgentId", delegateAgent.agentId(), "delegateAgentName", delegateAgent.agentName()),
                null,
                startedAt,
                LocalDateTime.now(),
                Map.of("entry", "delegation"),
                operatorId
        );
        try {
            LocalDateTime executeStartedAt = LocalDateTime.now();
            String responseText = runtimeLlmGatewayService.chatCompletion(systemPrompt, userPrompt, delegateModel);
            String outputSnapshot = toJsonText(Map.of(
                    "delegateAgentId", delegateAgent.agentId(),
                    "delegateAgentCode", delegateAgent.agentCode(),
                    "delegateAgentName", delegateAgent.agentName(),
                    "delegateInstruction", delegateInstruction,
                    "delegateResponse", responseText,
                    "delegationId", delegationId,
                    "childTaskId", childTaskId,
                    "childTraceId", childTraceId,
                    "promptTrace", buildPromptTraceMetadata(executionContext, systemPrompt, userPrompt)
            ));
            conversationRepository.updateTaskStepStatus(childTaskStepId, "success", outputSnapshot, null, operatorId);
            conversationRepository.updateTaskDelegationStatus(delegationId, "success", outputSnapshot, null, operatorId);
            conversationRepository.updateTaskStatus(childTaskId, "success", null, operatorId);
            taskTraceService.recordSpan(
                    childTaskId,
                    childTraceId,
                    childTaskStepId,
                    "delegation_execute",
                    "委派 Agent 执行子任务",
                    "success",
                    Map.of("delegateInstruction", delegateInstruction),
                    safeJson(outputSnapshot),
                    null,
                    executeStartedAt,
                    LocalDateTime.now(),
                    Map.of("delegateAgentId", delegateAgent.agentId(), "delegationId", delegationId,
                            "delegationDepth", governance.depth(), "selectionReason", governance.selectionReason()),
                    operatorId
            );
            taskTraceService.recordSpan(
                    childTaskId,
                    childTraceId,
                    null,
                    "task_result",
                    "委派子任务结束",
                    "success",
                    Map.of("childTaskId", childTaskId),
                    Map.of("status", "success"),
                    null,
                    executeStartedAt,
                    LocalDateTime.now(),
                    Map.of("entry", "delegation"),
                    operatorId
            );
            costGovernanceService.recordDelegation(executionContext, delegateAgent.agentCode(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - meteringStartedAt), "success");
            return new DelegationExecutionResult(
                    "已委派 Agent[" + delegateAgent.agentName() + "] 完成子任务",
                    responseText,
                    Map.of(
                            "delegateAgentId", delegateAgent.agentId(),
                            "delegateAgentCode", delegateAgent.agentCode(),
                            "delegateAgentName", delegateAgent.agentName(),
                            "delegateInstruction", delegateInstruction,
                            "delegateResponse", responseText,
                            "delegationId", delegationId,
                            "childTaskId", childTaskId,
                            "childTraceId", childTraceId
                    )
            );
        } catch (Exception exception) {
            costGovernanceService.recordDelegation(executionContext, delegateAgent.agentCode(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - meteringStartedAt), "failed");
            String errorSnapshot = toJsonText(Map.of(
                    "delegateAgentId", delegateAgent.agentId(),
                    "delegateInstruction", delegateInstruction,
                    "errorMessage", exception.getMessage(),
                    "delegationId", delegationId,
                    "childTaskId", childTaskId,
                    "childTraceId", childTraceId
            ));
            conversationRepository.updateTaskStepStatus(childTaskStepId, "failed", errorSnapshot, exception.getMessage(), operatorId);
            conversationRepository.updateTaskDelegationStatus(delegationId, "failed", errorSnapshot, exception.getMessage(), operatorId);
            conversationRepository.updateTaskStatus(childTaskId, "failed", exception.getMessage(), operatorId);
            taskTraceService.recordSpan(
                    childTaskId,
                    childTraceId,
                    childTaskStepId,
                    "delegation_execute",
                    "委派 Agent 执行子任务",
                    "failed",
                    Map.of("delegateInstruction", delegateInstruction),
                    Map.of(),
                    exception.getMessage(),
                    startedAt,
                    LocalDateTime.now(),
                    Map.of("delegateAgentId", delegateAgent.agentId(), "delegationId", delegationId),
                    operatorId
            );
            taskTraceService.recordSpan(
                    childTaskId,
                    childTraceId,
                    null,
                    "task_result",
                    "委派子任务结束",
                    "failed",
                    Map.of("childTaskId", childTaskId),
                    Map.of("status", "failed"),
                    exception.getMessage(),
                    startedAt,
                    LocalDateTime.now(),
                    Map.of("entry", "delegation"),
                    operatorId
            );
            throw new BusinessException("handoff 执行失败: " + exception.getMessage());
        }
    }

    private ExecutionContext.PromptContext resolveDelegatePrompt(AgentSnapshot delegateSnapshot, String authorization) {
        if (delegateSnapshot.defaultPromptId() == null) {
            return new ExecutionContext.PromptContext(
                    null,
                    null,
                    delegateSnapshot.defaultPromptName(),
                    null,
                    null,
                    null
            );
        }
        return registryAgentClient.getPromptSnapshot(delegateSnapshot.defaultPromptId(), authorization);
    }

    private ExecutionContext.ModelContext resolveDelegateModel(AgentSnapshot delegateSnapshot, String authorization) {
        if (delegateSnapshot.defaultModel() == null || delegateSnapshot.defaultModel().isBlank()) {
            return null;
        }
        return registryAgentClient.getModelSnapshot(delegateSnapshot.defaultModel(), authorization);
    }

    private String buildSystemPrompt(ExecutionContext.DelegateAgent delegateAgent,
                                     ExecutionContext.PromptContext delegatePrompt) {
        String promptContent = delegatePrompt == null || delegatePrompt.promptContent() == null
                ? ""
                : delegatePrompt.promptContent().trim();
        return """
                你现在作为被委派的专业 Agent 参与协作。
                你的职责是接手主 Agent 分配的一个子任务，并直接给出清晰、可执行、可复用的结果。
                请只围绕委派任务输出，不要重复解释协作机制。
                """ + "\n\n目标 Agent：" + delegateAgent.agentName()
                + "\nAgent 编码：" + delegateAgent.agentCode()
                + "\n专业说明：" + (delegateAgent.description() == null ? "" : delegateAgent.description())
                + "\n\n该 Agent 的默认 Prompt：\n" + promptContent;
    }

    private String buildUserPrompt(ExecutionContext executionContext,
                                   ExecutionPlan.ExecutionPlanStep step,
                                   String delegateInstruction,
                                   List<RuntimeStepResultContext> previousStepResults,
                                   Long delegateAgentId,
                                   String authorization) {
        List<String> previousSummaries = previousStepResults.stream()
                .map(item -> item.stepNo() + ". " + item.stepName() + " - " + item.summary())
                .toList();
        List<String> delegateTools = registryAgentClient.getEffectiveTools(delegateAgentId, authorization).stream()
                .map(item -> item.toolCode() + ":" + item.toolName())
                .toList();
        List<String> delegateWorkflows = registryAgentClient.getAvailableWorkflows(delegateAgentId, authorization).stream()
                .map(item -> item.workflowCode() + ":" + item.workflowName())
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("originalUserMessage", executionContext.userMessage());
        payload.put("parentAgent", executionContext.agentContext().agentName());
        payload.put("conversationTitle", executionContext.conversationContext().title());
        payload.put("parentGoal", executionContext.userMessage());
        payload.put("handoffStepId", step.stepId());
        payload.put("handoffStepName", step.stepName());
        payload.put("handoffObjective", step.objective());
        payload.put("delegateInstruction", delegateInstruction);
        payload.put("conversationState", executionContext.conversationStatePayload());
        payload.put("conversationStateText", executionContext.conversationStatePromptText());
        payload.put("conversationMemory", executionContext.conversationMemoryText());
        payload.put("userMemory", executionContext.userMemoryText());
        payload.put("recentTaskMemory", executionContext.recentTaskMemoryText());
        payload.put("previousStepResults", previousSummaries);
        payload.put("delegateAvailableTools", delegateTools);
        payload.put("delegateAvailableWorkflows", delegateWorkflows);
        payload.put("outputRequirement", "请直接给出本次子任务的最终结论和必要的执行建议，不要输出 JSON。");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("handoff 输入序列化失败");
        }
    }

    private ExecutionContext.ModelContext withDelegationTimeout(ExecutionContext.ModelContext model,
                                                                 int timeoutSeconds,
                                                                 com.zc.agent.core.modules.runtime.model.LlmCallBudget callBudget) {
        if (model == null) return null;
        int effectiveTimeout = model.timeoutSeconds() == null ? timeoutSeconds : Math.min(model.timeoutSeconds(), timeoutSeconds);
        return new ExecutionContext.ModelContext(model.modelCode(), model.modelName(), model.provider(), model.modelType(),
                model.baseUrl(), model.apiKey(), model.apiPath(), model.modelIdentifier(), model.temperature(), model.maxTokens(),
                model.contextWindow(), model.reservedOutputTokens(), model.reservedSystemTokens(), model.compactionTriggerRatio(),
                model.tokenizerFamily(), effectiveTimeout, model.status(), model.metadata(), callBudget);
    }

    private String resolveDelegateInstruction(ExecutionPlan.ExecutionPlanStep step, ExecutionContext executionContext) {
        if (step.inputs() != null) {
            Object value = step.inputs().get("delegateInstruction");
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        if (step.objective() != null && !step.objective().isBlank()) {
            return step.objective();
        }
        return executionContext.userMessage();
    }

    private Map<String, Object> buildPromptTraceMetadata(ExecutionContext executionContext,
                                                         String systemPrompt,
                                                         String userPrompt) {
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(
                executionContext,
                promptContextBudgetService.resolveBudget(
                        executionContext.modelForStage("reply"),
                        PromptContextBudgetService.PromptStage.REPLY
                )
        );
        return contextCompactionService.enrichPromptAssemblyMetadata(compacted.metadata(), systemPrompt, userPrompt);
    }

    public record DelegationExecutionResult(
            String summary,
            String responseText,
            Map<String, Object> outputSnapshot
    ) {
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("handoff 快照序列化失败");
        }
    }

    private Object safeJson(String jsonText) {
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", jsonText);
        }
    }

    private String nextTaskCode() {
        return "TASK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private String nextTraceId() {
        return "TRACE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
