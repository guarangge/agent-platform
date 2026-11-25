package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.memory.service.MemoryInjectionService;
import com.zc.agent.core.modules.conversation.service.AgentRunStreamEventService;
import com.zc.agent.core.modules.conversation.service.ConversationPolicyService;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行响应收口服务。
 * 负责统一拼装 direct reply、多步骤执行和汇总响应。
 */
@Service
public class ExecutionResponseAssemblerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionResponseAssemblerService.class);

    private final MemoryInjectionService memoryInjectionService;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionService contextCompactionService;
    private final AgentRunStreamEventService agentRunStreamEventService;
    private final ConversationPolicyService conversationPolicyService;
    private final ObjectMapper objectMapper;
    private final ImageInputCapabilityRouter imageInputCapabilityRouter;

    @Autowired
    public ExecutionResponseAssemblerService(MemoryInjectionService memoryInjectionService,
                                             RuntimeLlmGatewayService runtimeLlmGatewayService,
                                             PromptContextBudgetService promptContextBudgetService,
                                             ContextCompactionService contextCompactionService,
                                             AgentRunStreamEventService agentRunStreamEventService,
                                             ConversationPolicyService conversationPolicyService,
                                             ObjectMapper objectMapper,
                                             ImageInputCapabilityRouter imageInputCapabilityRouter) {
        this.memoryInjectionService = memoryInjectionService;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.promptContextBudgetService = promptContextBudgetService;
        this.contextCompactionService = contextCompactionService;
        this.agentRunStreamEventService = agentRunStreamEventService;
        this.conversationPolicyService = conversationPolicyService;
        this.objectMapper = objectMapper;
        this.imageInputCapabilityRouter = imageInputCapabilityRouter;
    }

    /** 保留原构造契约，供不启用多模态路由的独立单元测试使用。 */
    public ExecutionResponseAssemblerService(MemoryInjectionService memoryInjectionService,
                                              RuntimeLlmGatewayService runtimeLlmGatewayService,
                                              PromptContextBudgetService promptContextBudgetService,
                                              ContextCompactionService contextCompactionService,
                                              AgentRunStreamEventService agentRunStreamEventService,
                                              ConversationPolicyService conversationPolicyService,
                                              ObjectMapper objectMapper) {
        this(memoryInjectionService, runtimeLlmGatewayService, promptContextBudgetService, contextCompactionService,
                agentRunStreamEventService, conversationPolicyService, objectMapper, null);
    }

    /**
     * 组装 direct reply 场景的最小响应。
     *
     * @param executionContext  运行时上下文
     * @param executionDecision 决策结果
     * @param executionPlan     执行计划
     * @return 助手响应
     */
    public String buildDirectReplyResponse(ExecutionContext executionContext,
                                           ExecutionDecision executionDecision,
                                           ExecutionPlan executionPlan) {
        MemoryInjectionService.ResolvedMemoryInput directReplyInput = memoryInjectionService.resolveDirectReplyInput(executionContext);
        return generateReplyByLlm(
                executionContext,
                buildDirectReplySystemPrompt(executionContext),
                buildDirectReplyUserPrompt(executionContext, executionDecision, executionPlan, directReplyInput),
                buildDirectReplyFallback(executionContext, executionDecision, executionPlan, directReplyInput)
        );
    }

    /**
     * 为计划中的 reply 步骤生成自然语言回复。
     *
     * @param executionContext   运行时上下文
     * @param executionDecision  决策结果
     * @param executionPlan      执行计划
     * @param step               当前 reply 步骤
     * @param previousStepResults 前序步骤结果
     * @return 回复文本
     */
    public String buildReplyStepResponse(ExecutionContext executionContext,
                                         ExecutionDecision executionDecision,
                                         ExecutionPlan executionPlan,
                                         ExecutionPlan.ExecutionPlanStep step,
                                         List<RuntimeStepResultContext> previousStepResults) {
        return buildReplyStepResult(executionContext, executionDecision, executionPlan, step, previousStepResults).assistantMessage();
    }

    public StructuredStepResult buildReplyStepResult(ExecutionContext executionContext,
                                                     ExecutionDecision executionDecision,
                                                     ExecutionPlan executionPlan,
                                                     ExecutionPlan.ExecutionPlanStep step,
                                                     List<RuntimeStepResultContext> previousStepResults) {
        String systemPrompt = buildReplyStepSystemPrompt(executionContext, step);
        String userPrompt = buildReplyStepUserPrompt(executionContext, executionDecision, executionPlan, step, previousStepResults);
        String assistantMessage = generateReplyByLlm(
                executionContext,
                systemPrompt,
                userPrompt,
                buildReplyStepFallback(executionContext, step, previousStepResults)
        );
        return buildStructuredTextResult(
                "reply",
                assistantMessage,
                safeText(step.objective()),
                previousStepResults,
                buildPromptTraceMetadata(executionContext, PromptContextBudgetService.PromptStage.REPLY, systemPrompt, userPrompt)
        );
    }

    public StructuredStepResult buildWorkflowReplyNodeResult(ExecutionContext executionContext,
                                                             String workflowName,
                                                             String nodeName,
                                                             String objective,
                                                             List<RuntimeStepResultContext> previousStepResults) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        String systemPrompt = """
                你是 Workflow 内的 reply 节点生成器。
                请基于前序节点已确认的事实输出当前节点结果。
                要求：
                1. 不要编造未在事实上出现的具体细节。
                2. 信息不足时明确说明缺口。
                3. 如适合对比说明，优先使用简洁 Markdown 表格。
                4. previousNodeFacts 中 artifacts 非空时，必须明确产物已生成并可下载，不得输出相反结论。
                """
                + "\n\n默认 Prompt：\n" + safePromptContent(executionContext)
                + "\n\nWorkflow：" + safeText(workflowName)
                + "\n当前节点：" + safeText(nodeName)
                + "\n节点目标：" + safeText(objective);
        String userPrompt = toJsonText(Map.of(
                "userMessage", compacted.conversationAwareUserMessage(),
                "protectedFacts", compacted.protectedFactsPayload(),
                "conversationState", executionContext.conversationStatePayload(),
                "conversationStateText", trimText(safeText(executionContext.conversationStatePromptText()), budget.conversationStateChars()),
                "conversationMemory", trimText(safeText(buildConversationMemoryForPrompt(executionContext)), budget.conversationMemoryChars()),
                "workflowName", safeText(workflowName),
                "nodeName", safeText(nodeName),
                "objective", safeText(objective),
                "previousNodeFacts", previousStepResults.stream().map(item -> buildReplyFactSnapshot(item, budget)).toList()
        ));
        String assistantMessage = generateReplyByLlm(
                executionContext,
                systemPrompt,
                userPrompt,
                buildReplyNodeFallback(executionContext, nodeName, safeText(objective), previousStepResults)
        );
        return buildStructuredTextResult(
                "reply",
                assistantMessage,
                safeText(objective),
                previousStepResults,
                buildPromptTraceMetadata(executionContext, PromptContextBudgetService.PromptStage.REPLY, systemPrompt, userPrompt)
        );
    }

    public StructuredStepResult buildReflectionStepResult(ExecutionContext executionContext,
                                                          ExecutionDecision executionDecision,
                                                          ExecutionPlan executionPlan,
                                                          ExecutionPlan.ExecutionPlanStep step,
                                                          List<RuntimeStepResultContext> previousStepResults) {
        String systemPrompt = buildReflectionSystemPrompt(executionContext, step.stepName(), step.objective());
        String userPrompt = buildReflectionUserPrompt(
                executionContext,
                buildConversationAwareUserMessage(
                        executionContext,
                        resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REFLECTION)
                ),
                buildConversationMemoryForPrompt(executionContext),
                executionPlan.goal(),
                previousStepResults,
                step.inputs()
        );
        return evaluateReflection(
                executionContext,
                systemPrompt,
                userPrompt,
                buildPromptTraceMetadata(executionContext, PromptContextBudgetService.PromptStage.REFLECTION, systemPrompt, userPrompt),
                buildReflectionFallback(step.stepName(), step.objective(), previousStepResults)
        );
    }

    public StructuredStepResult buildWorkflowReflectionNodeResult(ExecutionContext executionContext,
                                                                  String workflowName,
                                                                  String nodeName,
                                                                  String objective,
                                                                  Map<String, Object> inputs,
                                                                  List<RuntimeStepResultContext> previousStepResults) {
        String systemPrompt = buildReflectionSystemPrompt(executionContext, nodeName, objective)
                + "\n\n当前位于 Workflow：「" + safeText(workflowName) + "」";
        String userPrompt = buildReflectionUserPrompt(
                executionContext,
                buildConversationAwareUserMessage(
                        executionContext,
                        resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REFLECTION)
                ),
                buildConversationMemoryForPrompt(executionContext),
                workflowName,
                previousStepResults,
                inputs
        );
        return evaluateReflection(
                executionContext,
                systemPrompt,
                userPrompt,
                buildPromptTraceMetadata(executionContext, PromptContextBudgetService.PromptStage.REFLECTION, systemPrompt, userPrompt),
                buildReflectionFallback(nodeName, objective, previousStepResults)
        );
    }

    /**
     * 汇总多步骤执行后的最终响应。
     *
     * @param executionContext    运行时上下文
     * @param executionDecision   决策结果
     * @param executionPlan       执行计划
     * @param responseParts       各步骤响应
     * @param previousStepResults 步骤摘要
     * @return 汇总后的助手响应
     */
    public String assemblePlanResponse(ExecutionContext executionContext,
                                       ExecutionDecision executionDecision,
                                       ExecutionPlan executionPlan,
                                       List<String> responseParts,
                                       List<RuntimeStepResultContext> previousStepResults) {
        String terminalResponse = resolveTerminalResponse(executionContext, executionPlan, previousStepResults);
        if (terminalResponse != null) {
            return terminalResponse;
        }
        if (responseParts.size() == 1) {
            return responseParts.get(0);
        }
        return generateReplyByLlm(
                executionContext,
                buildPlanSummarySystemPrompt(executionContext),
                buildPlanSummaryUserPrompt(executionContext, executionDecision, executionPlan, responseParts, previousStepResults),
                buildPlanSummaryFallback(responseParts, previousStepResults)
        );
    }

    private String resolveTerminalResponse(ExecutionContext executionContext,
                                           ExecutionPlan executionPlan,
                                           List<RuntimeStepResultContext> previousStepResults) {
        for (int index = previousStepResults.size() - 1; index >= 0; index--) {
            RuntimeStepResultContext result = previousStepResults.get(index);
            ExecutionStepType type;
            try {
                type = ExecutionStepType.requirePlanType(result.stepType());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if ((type == ExecutionStepType.REPLY || type == ExecutionStepType.CLARIFY)
                    && result.responseText() != null && !result.responseText().isBlank()) {
                return result.responseText();
            }
            if (type == ExecutionStepType.WORKFLOW
                    && isUserReplyWorkflowResult(result, executionContext, executionPlan)
                    && result.responseText() != null && !result.responseText().isBlank()) {
                return result.responseText();
            }
        }
        return null;
    }

    private boolean isUserReplyWorkflowResult(RuntimeStepResultContext result,
                                              ExecutionContext executionContext,
                                              ExecutionPlan executionPlan) {
        return executionPlan.steps().stream()
                .filter(step -> result.stepId().equals(step.stepId()))
                .map(ExecutionPlan.ExecutionPlanStep::targetWorkflowId)
                .filter(java.util.Objects::nonNull)
                .flatMap(workflowId -> executionContext.availableWorkflows().stream()
                        .filter(workflow -> workflowId.equals(workflow.workflowId())))
                .map(RegistryAgentWorkflow::metadata)
                .filter(java.util.Objects::nonNull)
                .map(metadata -> metadata.path("routing").path("outputMode").asText(""))
                .anyMatch("user_reply"::equalsIgnoreCase);
    }

    private String buildDirectReplySystemPrompt(ExecutionContext executionContext) {
        return """
                你是 Agent 最终回复生成器。
                请基于用户问题、Prompt 约束和记忆上下文，直接输出最终答复。
                要求：
                1. 不要暴露内部字段名、执行链路、决策类型、计划结构。
                2. 只输出面向用户的自然语言结果。
                3. 如果信息不足，明确说明缺口并给出下一步建议。
                """
                + "\n\n时间上下文：\n" + safeText(executionContext.currentTimeText())
                + "\n\n默认 Prompt：\n" + safePromptContent(executionContext);
    }

    private String buildDirectReplyUserPrompt(ExecutionContext executionContext,
                                              ExecutionDecision executionDecision,
                                              ExecutionPlan executionPlan,
                                              MemoryInjectionService.ResolvedMemoryInput directReplyInput) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userMessage", compacted.conversationAwareUserMessage());
        payload.put("protectedFacts", compacted.protectedFactsPayload());
        payload.put("currentDate", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().currentDate()));
        payload.put("currentDateTime", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().localDateTime()));
        payload.put("zoneId", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().zoneId()));
        payload.put("conversationMemory", compacted.conversationMemory());
        payload.put("conversationState", executionContext.conversationStatePayload());
        payload.put("conversationStateText", compacted.conversationStateText());
        payload.put("agentName", executionContext.agentContext().agentName());
        payload.put("decisionType", executionDecision.decisionType());
        payload.put("decisionReason", executionDecision.reason());
        payload.put("planType", executionPlan.planType());
        payload.put("memoryStrategy", directReplyInput.memoryStrategy());
        payload.put("memoryInput", trimText(safeText(directReplyInput.resolvedInputText()), budget.recentTaskMemoryChars()));
        payload.put("recentConversationSummary", compacted.recentConversationSummary());
        payload.put("recentUserMessages", compacted.recentUserMessages());
        payload.put("contextCompaction", compacted.metadata());
        return toJsonText(payload);
    }

    private String buildReplyStepSystemPrompt(ExecutionContext executionContext,
                                              ExecutionPlan.ExecutionPlanStep step) {
        return """
                你是 Agent 执行器中的 reply 步骤生成器。
                请只根据当前步骤目标和前序执行事实，输出当前步骤应返回给用户的内容。
                要求：
                1. 不要重复描述内部执行链路。
                2. 回答必须服务于当前步骤 objective。
                3. 只能使用已提供的执行事实，不得补充未在事实中出现的具体数字、年份、批次数量、院校规则或政策细节。
                4. 如前序结果不足以支撑结论，明确说明不足并收敛到当前步骤目标。
                5. 如适合对比说明，优先使用简洁 Markdown 表格。
                6. previousStepFacts 中的 artifacts 是已生成且可下载的真实产物；只要 artifacts 非空，禁止声称文件未生成、没有下载链接或审批未通过。
                """
                + "\n\n时间上下文：\n" + safeText(executionContext.currentTimeText())
                + "\n\n默认 Prompt：\n" + safePromptContent(executionContext)
                + "\n\n当前步骤：" + safeText(step.stepName()) + " / " + safeText(step.objective());
    }

    private String buildReplyStepUserPrompt(ExecutionContext executionContext,
                                            ExecutionDecision executionDecision,
                                            ExecutionPlan executionPlan,
                                            ExecutionPlan.ExecutionPlanStep step,
                                            List<RuntimeStepResultContext> previousStepResults) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userMessage", compacted.conversationAwareUserMessage());
        payload.put("protectedFacts", compacted.protectedFactsPayload());
        payload.put("currentDate", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().currentDate()));
        payload.put("currentDateTime", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().localDateTime()));
        payload.put("zoneId", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().zoneId()));
        payload.put("conversationMemory", compacted.conversationMemory());
        payload.put("conversationState", executionContext.conversationStatePayload());
        payload.put("conversationStateText", compacted.conversationStateText());
        payload.put("recentTaskSummary", compacted.recentTaskMemory());
        payload.put("decisionType", executionDecision.decisionType());
        payload.put("planGoal", executionPlan.goal());
        payload.put("stepName", step.stepName());
        payload.put("stepObjective", step.objective());
        payload.put("expectedOutput", step.expectedOutput());
        payload.put("recentConversationSummary", compacted.recentConversationSummary());
        payload.put("recentUserMessages", compacted.recentUserMessages());
        payload.put("contextCompaction", compacted.metadata());
        payload.put("previousStepFacts", previousStepResults.stream()
                .map(item -> buildReplyFactSnapshot(item, budget))
                .toList());
        return toJsonText(payload);
    }

    private String buildPlanSummarySystemPrompt(ExecutionContext executionContext) {
        return """
                你是 Agent 最终结果汇总器。
                请基于已执行步骤结果，输出一段对用户友好的最终答复。
                要求：
                1. 优先给结论，再给关键依据。
                2. 不要逐条复述全部内部步骤，除非这对理解结果必要。
                3. 不要暴露内部字段名和技术实现细节。
                4. 前序事实中存在 artifacts 时，必须明确文件已生成并可下载，不得输出相反结论。
                """
                + "\n\n时间上下文：\n" + safeText(executionContext.currentTimeText())
                + "\n\n默认 Prompt：\n" + safePromptContent(executionContext);
    }

    private String buildPlanSummaryUserPrompt(ExecutionContext executionContext,
                                              ExecutionDecision executionDecision,
                                              ExecutionPlan executionPlan,
                                              List<String> responseParts,
                                              List<RuntimeStepResultContext> previousStepResults) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionType", executionDecision.decisionType());
        payload.put("protectedFacts", compacted.protectedFactsPayload());
        payload.put("currentDate", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().currentDate()));
        payload.put("currentDateTime", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().localDateTime()));
        payload.put("zoneId", safeText(executionContext.timeContext() == null ? null : executionContext.timeContext().zoneId()));
        payload.put("planType", executionPlan.planType());
        payload.put("planGoal", executionPlan.goal());
        payload.put("conversationMemory", compacted.conversationMemory());
        payload.put("conversationState", executionContext.conversationStatePayload());
        payload.put("conversationStateText", compacted.conversationStateText());
        payload.put("recentConversationSummary", compacted.recentConversationSummary());
        payload.put("recentUserMessages", compacted.recentUserMessages());
        payload.put("contextCompaction", compacted.metadata());
        payload.put("stepSummaries", previousStepResults.stream()
                .map(item -> Map.of(
                        "stepNo", item.stepNo(),
                        "stepName", item.stepName(),
                        "summary", trimText(safeText(item.summary()), budget.previousSummaryChars())
                ))
                .toList());
        payload.put("stepResponses", responseParts.stream()
                .map(item -> trimText(safeText(item), budget.factChars()))
                .toList());
        return toJsonText(payload);
    }

    private String generateReplyByLlm(ExecutionContext executionContext,
                                      String systemPrompt,
                                      String userPrompt,
                                      String fallbackText) {
        if (!executionContext.hasRunnableModel("reply")) {
            return fallbackText;
        }
        try {
            ExecutionContext.ModelContext replyModel = executionContext.modelForStage("reply");
            ImageInputCapabilityRouter.PreparedImageInput preparedInput = imageInputCapabilityRouter == null
                    ? new ImageInputCapabilityRouter.PreparedImageInput(userPrompt,
                    ImageInputCapabilityRouter.ROUTE_TEXT_ONLY, true)
                    : imageInputCapabilityRouter.prepare(executionContext, userPrompt, replyModel);
            if (shouldUseNativeStreaming(executionContext)) {
                String runCode = AgentRunStreamingContext.currentRunCode();
                agentRunStreamEventService.publishProgress("generation", "模型正在生成回复", Map.of());
                agentRunStreamEventService.publishReset(runCode);
                NativeDeltaPublisher publisher = new NativeDeltaPublisher(runCode);
                try {
                    try {
                        return preparedInput.includeNativeImages()
                                ? runtimeLlmGatewayService.chatCompletionStreaming(
                                systemPrompt, preparedInput.userPrompt(), executionContext, replyModel, publisher::accept).trim()
                                : runtimeLlmGatewayService.chatCompletionStreaming(
                                systemPrompt, preparedInput.userPrompt(), replyModel, publisher::accept).trim();
                    } catch (Exception streamException) {
                        com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(streamException);
                        if (Thread.currentThread().isInterrupted()) throw streamException;
                        log.warn("模型原生流式调用失败，回退同模型非流式调用：{}", streamException.getMessage());
                        publisher.discard();
                        agentRunStreamEventService.publishReset(runCode);
                        return preparedInput.includeNativeImages()
                                ? runtimeLlmGatewayService.chatCompletion(
                                systemPrompt, preparedInput.userPrompt(), executionContext, replyModel).trim()
                                : runtimeLlmGatewayService.chatCompletion(
                                systemPrompt, preparedInput.userPrompt(), replyModel).trim();
                    }
                } finally {
                    publisher.flush();
                }
            }
            return preparedInput.includeNativeImages()
                    ? runtimeLlmGatewayService.chatCompletion(
                    systemPrompt, preparedInput.userPrompt(), executionContext, replyModel).trim()
                    : runtimeLlmGatewayService.chatCompletion(
                    systemPrompt, preparedInput.userPrompt(), replyModel).trim();
        } catch (ImageInputCapabilityRouter.ImageInputRoutingException exception) {
            log.warn("图片输入能力路由失败：{}", exception.getMessage());
            return exception.getMessage();
        } catch (com.zc.agent.core.modules.workflow.service.ApprovalRequiredException exception) {
            throw exception;
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            log.warn("回复生成失败，回退模板响应：{}", exception.getMessage());
            return fallbackText;
        }
    }

    private boolean shouldUseNativeStreaming(ExecutionContext executionContext) {
        return AgentRunStreamingContext.isFinalResponseScope()
                && AgentRunStreamingContext.currentRunCode() != null
                && conversationPolicyService.resolveResponseStreamingEnabled(
                        executionContext.conversationContext().metadata())
                && runtimeLlmGatewayService.supportsStreaming(executionContext.modelForStage("reply"));
    }

    /** 首个 delta 立即发布，后续小片段短暂合并，避免每个 token 都产生数据库事务。 */
    private final class NativeDeltaPublisher {
        private static final int BATCH_CHARACTERS = 48;
        private static final long MAX_BATCH_NANOS = 80_000_000L;

        private final String runCode;
        private final StringBuilder pending = new StringBuilder();
        private long lastPublishedAt;
        private boolean firstPublished;

        private NativeDeltaPublisher(String runCode) {
            this.runCode = runCode;
        }

        private void accept(String delta) {
            if (delta == null || delta.isEmpty()) return;
            if (!firstPublished) {
                agentRunStreamEventService.publishDelta(runCode, delta);
                firstPublished = true;
                lastPublishedAt = System.nanoTime();
                return;
            }
            pending.append(delta);
            if (pending.length() >= BATCH_CHARACTERS
                    || System.nanoTime() - lastPublishedAt >= MAX_BATCH_NANOS) {
                flush();
            }
        }

        private void flush() {
            if (pending.isEmpty()) return;
            agentRunStreamEventService.publishDelta(runCode, pending.toString());
            pending.setLength(0);
            lastPublishedAt = System.nanoTime();
        }

        private void discard() {
            pending.setLength(0);
        }
    }

    private String buildDirectReplyFallback(ExecutionContext executionContext,
                                            ExecutionDecision executionDecision,
                                            ExecutionPlan executionPlan,
                                            MemoryInjectionService.ResolvedMemoryInput directReplyInput) {
        StringBuilder builder = new StringBuilder();
        builder.append("已收到你的消息：").append(executionContext.userMessage()).append("。");
        builder.append("当前会话已绑定 Agent「").append(executionContext.agentContext().agentName()).append("」");
        if (executionContext.promptContext() != null && executionContext.promptContext().promptName() != null) {
            builder.append("，默认 Prompt 为「").append(executionContext.promptContext().promptName()).append("」");
        }
        builder.append("。当前决策结果为「").append(executionDecision.decisionType()).append("」");
        builder.append("，执行计划为「").append(executionPlan.planType()).append("」。");
        if (directReplyInput.resolvedInputText() != null && !directReplyInput.resolvedInputText().isBlank()) {
            builder.append("结合当前上下文，建议优先基于已有记忆继续处理该问题。");
        } else {
            builder.append("当前上下文记忆较少，建议继续补充目标、约束或期望输出。");
        }
        String contextualFallback = buildContextAwareFallback(executionContext, executionPlan.goal());
        if (!contextualFallback.isBlank()) {
            builder.append("\n\n").append(contextualFallback);
        }
        return builder.toString();
    }

    private String buildReplyStepFallback(ExecutionContext executionContext,
                                          ExecutionPlan.ExecutionPlanStep step,
                                          List<RuntimeStepResultContext> previousStepResults) {
        return buildReplyNodeFallback(executionContext, step.stepName(), safeText(step.objective()), previousStepResults);
    }

    private String buildReplyNodeFallback(ExecutionContext executionContext,
                                          String nodeName,
                                          String objective,
                                          List<RuntimeStepResultContext> previousStepResults) {
        if (!previousStepResults.isEmpty()) {
            RuntimeStepResultContext lastStep = previousStepResults.get(previousStepResults.size() - 1);
            return "已完成步骤「" + nodeName + "」，结合前序结果可继续推进："
                    + safeText(lastStep.summary())
                    + "。"
                    + trimText(safeText(lastStep.responseText()), 240);
        }
        String contextualFallback = buildContextAwareFallback(executionContext, objective);
        if (!contextualFallback.isBlank()) {
            return contextualFallback;
        }
        return "已完成步骤「" + nodeName + "」。";
    }

    private String buildPlanSummaryFallback(List<String> responseParts,
                                            List<RuntimeStepResultContext> previousStepResults) {
        StringBuilder builder = new StringBuilder("已按计划完成 ")
                .append(responseParts.size())
                .append(" 个步骤。");
        if (!previousStepResults.isEmpty()) {
            builder.append("\n步骤摘要：");
            for (RuntimeStepResultContext item : previousStepResults) {
                builder.append("\n")
                        .append(item.stepNo())
                        .append(". ")
                        .append(item.stepName())
                        .append(" - ")
                        .append(item.summary());
            }
        }
        builder.append("\n\n详细结果：\n");
        builder.append(String.join("\n\n", responseParts));
        return builder.toString();
    }

    private String buildConversationAwareUserMessage(ExecutionContext executionContext,
                                                     PromptContextBudgetService.StageBudget budget) {
        return contextCompactionService.compactStageContext(executionContext, budget).conversationAwareUserMessage();
    }

    private String buildContextAwareFallback(ExecutionContext executionContext, String objective) {
        StringBuilder builder = new StringBuilder();
        if (!safeText(objective).isBlank()) {
            builder.append("我已结合你当前会话里的已有信息继续处理。");
            builder.append("当前这一步的目标是：").append(trimText(safeText(objective), 80)).append("。");
        } else {
            builder.append("我已结合你当前会话里的已有信息继续处理。");
        }
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        String recentSummary = executionContext.recentTaskMemoryContext() == null
                ? ""
                : trimText(safeText(executionContext.recentTaskMemoryContext().finalResponseSummary()),
                Math.min(180, budget.historyMessageChars()));
        if (!recentSummary.isBlank()) {
            builder.append("如果你希望我基于上一版结果继续调整，请直接说明要保留、删除或重排的部分。");
        } else {
            builder.append("如果你希望我继续细化，请补充本轮最关键的目标或约束。");
        }
        return builder.toString();
    }

    private String buildConversationMemoryForPrompt(ExecutionContext executionContext) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REPLY);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        String stateText = safeText(compacted.conversationStateText()).trim();
        String memoryText = safeText(compacted.conversationMemory()).trim();
        String knowledgeText = trimText(safeText(executionContext.knowledgeContextText()), budget.factChars() * 2).trim();
        String conversationText;
        if (stateText.isEmpty()) {
            conversationText = memoryText;
        } else if (memoryText.isEmpty()) {
            conversationText = stateText;
        } else {
            conversationText = stateText + "\n" + memoryText;
        }
        if (knowledgeText.isEmpty()) {
            return conversationText;
        }
        return conversationText.isEmpty() ? "知识上下文：\n" + knowledgeText
                : conversationText + "\n知识上下文：\n" + knowledgeText;
    }

    private String safePromptContent(ExecutionContext executionContext) {
        if (executionContext.promptContext() == null || executionContext.promptContext().promptContent() == null) {
            return "请生成准确、简洁、可执行的回答。";
        }
        return executionContext.promptContext().promptContent();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> buildReplyFactSnapshot(RuntimeStepResultContext item,
                                                       PromptContextBudgetService.StageBudget budget) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stepNo", item.stepNo());
        snapshot.put("stepType", item.stepType());
        snapshot.put("stepName", item.stepName());
        snapshot.put("summary", trimText(safeText(item.summary()), budget.previousSummaryChars()));
        snapshot.put("responseText", trimText(safeText(item.responseText()), budget.factChars()));
        StandardStepOutputSnapshot standardSnapshot = parseStandardSnapshot(item.outputSnapshot());
        if (standardSnapshot != null) {
            snapshot.put("observation", trimText(safeText(standardSnapshot.observation()), budget.factChars()));
            snapshot.put("confidence", standardSnapshot.confidence());
            snapshot.put("needReplan", standardSnapshot.needReplan());
            snapshot.put("needMoreInfo", standardSnapshot.needMoreInfo());
            snapshot.put("nextActionHint", trimText(safeText(standardSnapshot.nextActionHint()), budget.historyMessageChars()));
        }
        List<Map<String, Object>> artifacts = extractArtifactFacts(item.outputSnapshot());
        if (!artifacts.isEmpty()) {
            snapshot.put("artifacts", artifacts);
        }
        snapshot.put("grounding", extractGroundingFacts(item, budget));
        return snapshot;
    }

    /** 仅向 Reply Prompt 注入产物路径和大小，避免把 contentBase64 等大字段带入模型上下文。 */
    private List<Map<String, Object>> extractArtifactFacts(String outputSnapshot) {
        try {
            JsonNode artifactNodes = objectMapper.readTree(safeText(outputSnapshot)).path("payload").path("artifacts");
            if (!artifactNodes.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> artifacts = new java.util.ArrayList<>();
            for (JsonNode artifactNode : artifactNodes) {
                String path = artifactNode.path("path").asText("").trim();
                if (!path.isEmpty()) {
                    artifacts.add(Map.of(
                            "path", path,
                            "size", artifactNode.path("size").asLong(0),
                            "downloadable", true
                    ));
                }
            }
            return artifacts;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Object extractGroundingFacts(RuntimeStepResultContext item,
                                         PromptContextBudgetService.StageBudget budget) {
        StandardStepOutputSnapshot standardSnapshot = parseStandardSnapshot(item.outputSnapshot());
        if (standardSnapshot != null && standardSnapshot.evidence() != null && !standardSnapshot.evidence().isEmpty()) {
            return standardSnapshot.evidence();
        }
        if (!"workflow".equals(item.stepType())) {
            return List.of(Map.of(
                "summary", trimText(safeText(item.summary()), budget.previousSummaryChars()),
                "responseText", trimText(safeText(item.responseText()), budget.factChars())
            ));
        }
        try {
            JsonNode rootNode = objectMapper.readTree(safeText(item.outputSnapshot()));
            JsonNode executedNodes = rootNode.path("executedNodes");
            if (!executedNodes.isArray() || executedNodes.isEmpty()) {
                return List.of(Map.of(
                        "summary", trimText(safeText(item.summary()), budget.previousSummaryChars()),
                        "responseText", trimText(safeText(item.responseText()), budget.factChars())
                ));
            }
            List<Map<String, Object>> facts = new java.util.ArrayList<>();
            for (JsonNode node : executedNodes) {
                String nodeType = node.path("nodeType").asText();
                if (!"tool".equals(nodeType) && !"reply".equals(nodeType) && !"reflection".equals(nodeType) && !"handoff".equals(nodeType)) {
                    continue;
                }
                LinkedHashMap<String, Object> nodeFact = new LinkedHashMap<>();
                nodeFact.put("nodeName", node.path("nodeName").asText(""));
                nodeFact.put("nodeType", nodeType);
                nodeFact.put("toolCode", node.path("toolCode").asText(""));
                nodeFact.put("summary", trimText(node.path("summary").asText(""), budget.previousSummaryChars()));
                nodeFact.put("observation", trimText(node.path("observation").asText(""), budget.factChars()));
                nodeFact.put("responseText", trimText(node.path("responseText").asText(""), budget.factChars()));
                facts.add(nodeFact);
            }
            if (!facts.isEmpty()) {
                return facts;
            }
        } catch (Exception exception) {
            log.debug("解析 reply grounding 失败，回退基础结果: {}", exception.getMessage());
        }
        return List.of(Map.of(
                "summary", trimText(safeText(item.summary()), budget.previousSummaryChars()),
                "responseText", trimText(safeText(item.responseText()), budget.factChars())
        ));
    }

    private PromptContextBudgetService.StageBudget resolveBudget(ExecutionContext executionContext,
                                                                 PromptContextBudgetService.PromptStage stage) {
        return promptContextBudgetService.resolveBudget(
                executionContext == null ? null : executionContext.modelForStage(stage.name().toLowerCase()), stage);
    }

    private String trimText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private StructuredStepResult buildStructuredTextResult(String resultType,
                                                           String assistantMessage,
                                                           String objective,
                                                           List<RuntimeStepResultContext> previousStepResults,
                                                           Map<String, Object> extraMetadata) {
        String summary = objective == null || objective.isBlank() ? "已生成当前步骤回复" : objective;
        String observation = !previousStepResults.isEmpty()
                ? safeText(previousStepResults.get(previousStepResults.size() - 1).summary())
                : summary;
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("objective", safeText(objective));
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        return new StructuredStepResult(
                assistantMessage,
                summary,
                toJsonText(new StandardStepOutputSnapshot(
                        resultType,
                        assistantMessage,
                        assistantMessage,
                        summary,
                        observation,
                        0.8d,
                        false,
                        false,
                        "",
                        List.of(),
                        Map.of("objective", safeText(objective)),
                        Map.of("source", "llm_reply"),
                        metadata
                ))
        );
    }

    private String buildReflectionSystemPrompt(ExecutionContext executionContext,
                                               String stepName,
                                               String objective) {
        return """
                你是 Agent 执行链路中的 reflection / verification 节点。
                你的职责是基于前序步骤事实进行审查、校验和下一步判断。
                必须返回严格 JSON，不要 Markdown，不要解释。
                输出格式：
                {
                  "assistantMessage": "给用户或执行器看的简短结论",
                  "summary": "30字以内摘要",
                  "observation": "当前观察到的关键信号",
                  "confidence": 0.0,
                  "needReplan": false,
                  "needMoreInfo": false,
                  "nextActionHint": "建议的下一步",
                  "evidence": [
                    {
                      "sourceStepId": "step_x",
                      "point": "依据"
                    }
                  ]
                }
                """
                + "\n\n默认 Prompt：\n" + safePromptContent(executionContext)
                + "\n\n当前节点：" + safeText(stepName)
                + "\n节点目标：" + safeText(objective);
    }

    private String buildReflectionUserPrompt(ExecutionContext executionContext,
                                             String userMessage,
                                             String conversationMemory,
                                             String goal,
                                             List<RuntimeStepResultContext> previousStepResults,
                                             Map<String, Object> inputs) {
        PromptContextBudgetService.StageBudget budget = resolveBudget(executionContext, PromptContextBudgetService.PromptStage.REFLECTION);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userMessage", trimText(safeText(userMessage), budget.userMessageChars()));
        payload.put("conversationMemory", trimText(safeText(conversationMemory), budget.conversationMemoryChars()));
        payload.put("goal", trimText(safeText(goal), budget.factChars()));
        payload.put("reflectionConfig", inputs == null ? Map.of() : inputs);
        payload.put("previousStepFacts", previousStepResults.stream().map(item -> buildReplyFactSnapshot(item, budget)).toList());
        return toJsonText(payload);
    }

    private StructuredStepResult evaluateReflection(ExecutionContext executionContext,
                                                    String systemPrompt,
                                                    String userPrompt,
                                                    Map<String, Object> promptTraceMetadata,
                                                    ReflectionFallback fallback) {
        StandardStepOutputSnapshot snapshot = null;
        if (executionContext.hasRunnableModel("reflection")) {
            try {
                String responseText = runtimeLlmGatewayService.chatCompletion(systemPrompt, userPrompt, executionContext.modelForStage("reflection"));
                LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("reflectionConfigApplied", true);
                if (promptTraceMetadata != null && !promptTraceMetadata.isEmpty()) {
                    metadata.putAll(promptTraceMetadata);
                }
                JsonNode node = objectMapper.readTree(runtimeLlmGatewayService.sanitizeJsonText(responseText));
                snapshot = new StandardStepOutputSnapshot(
                        "reflection",
                        node.path("assistantMessage").asText(fallback.assistantMessage()),
                        node.path("assistantMessage").asText(fallback.assistantMessage()),
                        node.path("summary").asText(fallback.summary()),
                        node.path("observation").asText(fallback.observation()),
                        node.hasNonNull("confidence") ? node.path("confidence").asDouble(0.5d) : fallback.confidence(),
                        node.path("needReplan").asBoolean(fallback.needReplan()),
                        node.path("needMoreInfo").asBoolean(fallback.needMoreInfo()),
                        node.path("nextActionHint").asText(fallback.nextActionHint()),
                        extractEvidence(node.path("evidence")),
                        Map.of("summary", node.path("summary").asText(fallback.summary())),
                        Map.of("source", "llm_reflection"),
                        metadata
                );
            } catch (Exception exception) {
                com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
                log.warn("reflection 节点生成失败，回退模板响应：{}", exception.getMessage());
            }
        }
        if (snapshot == null) {
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("reflectionConfigApplied", false);
            if (promptTraceMetadata != null && !promptTraceMetadata.isEmpty()) {
                metadata.putAll(promptTraceMetadata);
            }
            snapshot = new StandardStepOutputSnapshot(
                    "reflection",
                    fallback.assistantMessage(),
                    fallback.assistantMessage(),
                    fallback.summary(),
                    fallback.observation(),
                    fallback.confidence(),
                    fallback.needReplan(),
                    fallback.needMoreInfo(),
                    fallback.nextActionHint(),
                    fallback.evidence(),
                    Map.of("summary", fallback.summary()),
                    Map.of("source", "reflection_fallback"),
                    metadata
            );
        }
        return new StructuredStepResult(snapshot.assistantMessage(), snapshot.summary(), toJsonText(snapshot));
    }

    private Map<String, Object> buildPromptTraceMetadata(ExecutionContext executionContext,
                                                         PromptContextBudgetService.PromptStage stage,
                                                         String systemPrompt,
                                                         String userPrompt) {
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(
                executionContext,
                resolveBudget(executionContext, stage)
        );
        return Map.of(
                "promptTrace",
                contextCompactionService.enrichPromptAssemblyMetadata(compacted.metadata(), systemPrompt, userPrompt)
        );
    }

    private ReflectionFallback buildReflectionFallback(String stepName,
                                                       String objective,
                                                       List<RuntimeStepResultContext> previousStepResults) {
        if (previousStepResults.isEmpty()) {
            return new ReflectionFallback(
                    "当前缺少可供校验的前序结果，建议先补充执行事实。",
                    safeText(objective).isBlank() ? "缺少前序事实" : safeText(objective),
                    "前序步骤为空",
                    0.2d,
                    false,
                    true,
                    "先执行信息采集或检索步骤",
                    List.of()
            );
        }
        RuntimeStepResultContext lastStep = previousStepResults.get(previousStepResults.size() - 1);
        boolean needMoreInfo = lastStep.responseText() == null || lastStep.responseText().isBlank();
        String observation = safeText(lastStep.summary()).isBlank() ? "已拿到前序步骤结果" : safeText(lastStep.summary());
        return new ReflectionFallback(
                "已完成「" + safeText(stepName) + "」校验，当前观察为：" + observation,
                safeText(objective).isBlank() ? "已完成步骤校验" : safeText(objective),
                observation,
                0.6d,
                false,
                needMoreInfo,
                needMoreInfo ? "补充关键输入后再继续执行" : "按当前计划继续执行",
                List.of(Map.of(
                        "sourceStepId", safeText(lastStep.stepId()),
                        "point", observation
                ))
        );
    }

    private List<Map<String, Object>> extractEvidence(JsonNode evidenceNode) {
        if (!evidenceNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> evidence = new java.util.ArrayList<>();
        for (JsonNode item : evidenceNode) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourceStepId", item.path("sourceStepId").asText(""));
            row.put("point", item.path("point").asText(""));
            evidence.add(row);
        }
        return evidence;
    }

    private StandardStepOutputSnapshot parseStandardSnapshot(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(outputSnapshot);
            if (!rootNode.isObject() || rootNode.path("resultType").asText("").isBlank()) {
                return null;
            }
            return objectMapper.treeToValue(rootNode, StandardStepOutputSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("响应生成输入序列化失败", exception);
        }
    }

    public record StructuredStepResult(
            String assistantMessage,
            String summary,
            String outputSnapshot
    ) {
    }

    private record ReflectionFallback(
            String assistantMessage,
            String summary,
            String observation,
            Double confidence,
            boolean needReplan,
            boolean needMoreInfo,
            String nextActionHint,
            List<Map<String, Object>> evidence
    ) {
    }
}
