package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.zc.agent.core.modules.eval.service.EvalFeedbackService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 执行决策服务。
 */
@Service
public class ExecutionDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionDecisionService.class);
    private static final String SANDBOX_EXECUTE_TOOL_CODE = "SANDBOX_EXECUTE";
    private final ObjectMapper objectMapper;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final EvalFeedbackService evalFeedbackService;
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionService contextCompactionService;
    private final AgentExecutionPolicyResolver executionPolicyResolver;

    public ExecutionDecisionService(ObjectMapper objectMapper,
                                    RuntimeLlmGatewayService runtimeLlmGatewayService,
                                    EvalFeedbackService evalFeedbackService,
                                    PromptContextBudgetService promptContextBudgetService,
                                    ContextCompactionService contextCompactionService,
                                    AgentExecutionPolicyResolver executionPolicyResolver) {
        this.objectMapper = objectMapper;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.evalFeedbackService = evalFeedbackService;
        this.promptContextBudgetService = promptContextBudgetService;
        this.contextCompactionService = contextCompactionService;
        this.executionPolicyResolver = executionPolicyResolver;
    }

    /**
     * 根据执行上下文生成决策结果。
     *
     * @param executionContext 运行时上下文
     * @return 决策结果
     */
    public ExecutionDecision decide(ExecutionContext executionContext) {
        ExecutionDecision artifactGenerationDecision = resolveArtifactGenerationDecision(executionContext);
        if (artifactGenerationDecision != null) {
            return artifactGenerationDecision;
        }
        AgentExecutionPolicyResolver.Policy policy = executionPolicyResolver.resolve(executionContext);
        if (!"llm_first".equals(policy.decisionMode())) {
            ExecutionDecision ruleDecision = decideByRules(executionContext, false);
            if (ruleDecision != null || "rules_only".equals(policy.decisionMode())) {
                return ruleDecision == null ? directReplyDecision("规则决策未命中可执行资源") : ruleDecision;
            }
        }
        ExecutionDecision llmDecision = decideByLlm(executionContext);
        if (llmDecision != null) {
            return llmDecision;
        }
        ExecutionDecision ruleDecision = decideByRules(executionContext, true);
        return ruleDecision == null ? directReplyDecision("当前未命中 Tool，也没有可执行 Workflow") : ruleDecision;
    }

    private ExecutionDecision decideByRules(ExecutionContext executionContext, boolean includeDirectReply) {
        RegistryAgentWorkflow matchedWorkflow = resolveMatchedWorkflow(executionContext);
        if (matchedWorkflow != null) {
            return new ExecutionDecision(
                    "workflow",
                    buildWorkflowDecisionReason(executionContext, matchedWorkflow),
                    List.of(),
                    List.of(new ExecutionDecision.SelectedWorkflow(matchedWorkflow.workflowId(), matchedWorkflow.workflowName()))
            );
        }
        List<RegistryAgentTool> matchedTools = resolveMatchedTools(executionContext);
        if (!matchedTools.isEmpty()) {
            return new ExecutionDecision(
                    "tool",
                    buildToolDecisionReason(executionContext, matchedTools),
                    buildSelectedTools(matchedTools),
                    List.of()
            );
        }
        ExecutionDecision followUpDecision = resolveFollowUpRefinementDecision(executionContext);
        if (followUpDecision != null) {
            return followUpDecision;
        }
        return includeDirectReply ? directReplyDecision("当前未命中 Tool，也没有可执行 Workflow") : null;
    }

    private ExecutionDecision directReplyDecision(String reason) {
        return new ExecutionDecision("direct_reply", reason + "，返回直接响应", List.of(), List.of());
    }

    private ExecutionDecision decideByLlm(ExecutionContext executionContext) {
        if (!executionContext.hasRunnableModel("decision")
                || executionContext.decisionPromptContext() == null
                || executionContext.decisionPromptContext().promptContent() == null
                || executionContext.decisionPromptContext().promptContent().isBlank()) {
            return null;
        }
        try {
            PromptTracePackage promptTracePackage = buildDecisionPromptTracePackage(executionContext);
            String responseText = runtimeLlmGatewayService.chatCompletion(
                    promptTracePackage.systemPrompt(),
                    promptTracePackage.userPrompt(),
                    executionContext.modelForStage("decision")
            );
            LlmDecisionResponse response = parseLlmDecisionResponse(responseText);
            ExecutionDecision executionDecision = toExecutionDecision(response, executionContext);
            if (executionDecision == null) {
                throw new IllegalStateException("LLM 决策缺少有效 decisionType，回退规则决策");
            }
            return executionDecision;
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            String failureAction = executionPolicyResolver.resolve(executionContext).decisionLlmFailureAction();
            if ("fail_fast".equals(failureAction)) {
                throw new com.zc.agent.core.common.exception.BusinessException("LLM 决策失败且策略要求直接失败：" + exception.getMessage());
            }
            if ("direct_reply".equals(failureAction)) {
                log.warn("LLM 决策失败，策略要求直接回复：{}", exception.getMessage());
                return new ExecutionDecision("direct_reply", "LLM 决策失败，按 Agent 策略降级为直接回复", List.of(), List.of());
            }
            log.warn("LLM 决策失败，按 Agent 策略回退规则决策：{}", exception.getMessage(), exception);
            return null;
        }
    }

    public Map<String, Object> buildDecisionTraceMetadata(ExecutionContext executionContext) {
        DecisionStrategy strategy = resolveDecisionStrategy(executionContext);
        if (!strategy.useLlmDecision()) {
            return Map.of(
                    "contextCompaction", Map.of("skipped", true, "reason", strategy.reason()),
                    "decisionStrategy", strategy.traceMetadata(),
                    "executionPolicy", executionPolicyResolver.resolve(executionContext).traceMetadata()
            );
        }
        try {
            return Map.of(
                    "contextCompaction",
                    buildDecisionPromptTracePackage(executionContext).compactionMetadata(),
                    "decisionStrategy", strategy.traceMetadata(),
                    "executionPolicy", executionPolicyResolver.resolve(executionContext).traceMetadata()
            );
        } catch (Exception exception) {
            log.warn("构建决策 Prompt Trace 元数据失败：{}", exception.getMessage());
            return Map.of(
                    "contextCompaction",
                    contextCompactionService.compactStageContext(
                            executionContext,
                            PromptContextBudgetService.PromptStage.DECISION
                    ).metadata(),
                    "decisionStrategy", strategy.traceMetadata(),
                    "executionPolicy", executionPolicyResolver.resolve(executionContext).traceMetadata()
            );
        }
    }

    DecisionStrategy resolveDecisionStrategy(ExecutionContext executionContext) {
        if (resolveArtifactGenerationDecision(executionContext) != null) {
            return new DecisionStrategy(false, "explicit_artifact_generation_tool");
        }
        AgentExecutionPolicyResolver.Policy policy = executionPolicyResolver.resolve(executionContext);
        if ("rules_only".equals(policy.decisionMode())) {
            return new DecisionStrategy(false, "policy_rules_only");
        }
        if ("rule_first".equals(policy.decisionMode()) && decideByRules(executionContext, false) != null) {
            return new DecisionStrategy(false, "rule_first_fast_path");
        }
        if (!executionContext.hasRunnableModel("decision")) {
            return new DecisionStrategy(false, "model_unavailable");
        }
        if (executionContext.decisionPromptContext() == null
                || executionContext.decisionPromptContext().promptContent() == null
                || executionContext.decisionPromptContext().promptContent().isBlank()) {
            return new DecisionStrategy(false, "decision_prompt_unavailable");
        }
        return new DecisionStrategy(true, "llm_decision_required");
    }

    record DecisionStrategy(boolean useLlmDecision, String reason) {
        Map<String, Object> traceMetadata() {
            return Map.of("useLlmDecision", useLlmDecision, "reason", reason);
        }
    }

    private RegistryAgentWorkflow resolveMatchedWorkflow(ExecutionContext executionContext) {
        String normalizedText = buildRoutingText(executionContext);
        RegistryAgentWorkflow metadataMatchedWorkflow = executionContext.availableWorkflows().stream()
                .filter(workflow -> scoreWorkflow(workflow, normalizedText) > 0)
                .max(Comparator.comparingInt(workflow -> scoreWorkflow(workflow, normalizedText)))
                .orElse(null);
        if (metadataMatchedWorkflow != null) {
            return metadataMatchedWorkflow;
        }
        RegistryAgentWorkflow approvalWorkflow = executionContext.availableWorkflows().stream()
                .filter(this::isApprovalWorkflow)
                .findFirst()
                .orElse(null);
        if (approvalWorkflow != null && containsApprovalKeyword(normalizedText)) {
            return approvalWorkflow;
        }
        if (executionContext.availableWorkflows().size() == 1 && containsWorkflowIntent(normalizedText)) {
            return executionContext.availableWorkflows().get(0);
        }
        return null;
    }

    /**
     * 对明确要求生成 PDF 文件的请求优先选择 Sandbox，避免多轮上下文误命中纯回复 Workflow。
     */
    private ExecutionDecision resolveArtifactGenerationDecision(ExecutionContext executionContext) {
        String userMessage = safeLowerText(executionContext.userMessage());
        if (!containsAny(userMessage, "pdf", ".pdf")
                || !containsAny(userMessage, "生成", "创建", "制作", "导出", "转换", "转成",
                "generate", "create", "export", "convert")) {
            return null;
        }
        RegistryAgentTool sandboxTool = executionContext.availableTools().stream()
                .filter(tool -> SANDBOX_EXECUTE_TOOL_CODE.equalsIgnoreCase(tool.toolCode()))
                .findFirst()
                .orElse(null);
        if (sandboxTool == null) {
            return null;
        }
        return new ExecutionDecision(
                "tool",
                "检测到明确的 PDF 文件生成请求，优先使用 Sandbox 实际生成可下载产物",
                List.of(new ExecutionDecision.SelectedTool(sandboxTool.toolCode(), sandboxTool.toolName())),
                List.of()
        );
    }

    private List<RegistryAgentTool> resolveMatchedTools(ExecutionContext executionContext) {
        String normalizedText = buildRoutingText(executionContext);
        return executionContext.availableTools().stream()
                .map(tool -> Map.entry(tool, scoreTool(tool, normalizedText)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<RegistryAgentTool, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean isApprovalWorkflow(RegistryAgentWorkflow workflow) {
        return containsApprovalKeyword(safeLowerText(workflow.workflowName()))
                || containsApprovalKeyword(safeLowerText(workflow.workflowCode()))
                || containsApprovalKeyword(safeLowerText(workflow.workflowType()))
                || containsApprovalKeyword(safeLowerText(workflow.workflowCategory()))
                || containsApprovalKeyword(safeLowerText(workflow.description()));
    }

    private int scoreWorkflow(RegistryAgentWorkflow workflow, String normalizedText) {
        JsonNode routingNode = routingNode(workflow.metadata());
        if (matchesAny(normalizedText, firstPresent(routingNode, "nonApplicableScenariosText", "nonApplicableScenarios"))) {
            return 0;
        }
        int score = 0;
        score += countMatches(normalizedText, firstPresent(routingNode, "triggerConditionsText", "triggerConditions"), 3);
        score += countMatches(normalizedText, firstPresent(routingNode, "applicableScenariosText", "applicableScenarios"), 2);
        score += countMatches(normalizedText, firstPresent(routingNode, "inputPrerequisitesText", "inputPrerequisites"), 1);
        score += countMatches(normalizedText, workflow.workflowName(), 1);
        score += countMatches(normalizedText, workflow.workflowCode(), 1);
        score += countMatches(normalizedText, workflow.workflowType(), 1);
        score += countMatches(normalizedText, workflow.workflowCategory(), 1);
        score += countMatches(normalizedText, workflow.description(), 1);
        return score;
    }

    private int scoreTool(RegistryAgentTool tool, String normalizedText) {
        JsonNode routingNode = routingNode(tool.metadata());
        if (matchesAny(normalizedText, firstPresent(routingNode, "limitationsText", "limitations"))) {
            return 0;
        }
        int score = 0;
        score += countMatches(normalizedText, firstPresent(routingNode, "triggerConditionsText", "triggerConditions"), 3);
        score += countMatches(normalizedText, firstPresent(routingNode, "capabilitiesText", "capabilities"), 2);
        score += countMatches(normalizedText, firstPresent(routingNode, "inputPrerequisitesText", "inputPrerequisites"), 1);
        score += countMatches(normalizedText, tool.toolName(), 1);
        score += countMatches(normalizedText, tool.toolCode(), 1);
        score += countMatches(normalizedText, tool.toolType(), 1);
        score += countMatches(normalizedText, tool.toolCategory(), 1);
        score += countMatches(normalizedText, tool.description(), 1);
        return score;
    }

    private String buildWorkflowDecisionReason(ExecutionContext executionContext, RegistryAgentWorkflow workflow) {
        String promptName = executionContext.decisionPromptContext() == null
                ? "未配置"
                : executionContext.decisionPromptContext().promptName();
        JsonNode routingNode = routingNode(workflow.metadata());
        String triggerConditions = compactText(firstPresentText(routingNode, "triggerConditionsText", "triggerConditions"));
        if (!triggerConditions.isEmpty()) {
            return "命中 Workflow 路由条件，按决策 Prompt[" + promptName + "] 选择 Workflow；触发条件：" + triggerConditions;
        }
        return "命中 Workflow 条件，按决策 Prompt[" + promptName + "] 进入 Workflow 分支";
    }

    private String buildToolDecisionReason(ExecutionContext executionContext, List<RegistryAgentTool> matchedTools) {
        String promptName = executionContext.decisionPromptContext() == null
                ? "未配置"
                : executionContext.decisionPromptContext().promptName();
        RegistryAgentTool firstTool = matchedTools.get(0);
        JsonNode routingNode = routingNode(firstTool.metadata());
        String triggerConditions = compactText(firstPresentText(routingNode, "triggerConditionsText", "triggerConditions"));
        if (matchedTools.size() > 1) {
            return "命中多个 Tool 路由条件，按决策 Prompt[" + promptName + "] 和摘要信息排序后执行";
        }
        if (!triggerConditions.isEmpty()) {
            return "命中 Tool 路由条件，按决策 Prompt[" + promptName + "] 选择 Tool；触发条件：" + triggerConditions;
        }
        return "命中 Tool 条件，按决策 Prompt[" + promptName + "] 进入 Tool 分支";
    }

    private List<ExecutionDecision.SelectedTool> buildSelectedTools(List<RegistryAgentTool> matchedTools) {
        return matchedTools.stream()
                .map(tool -> new ExecutionDecision.SelectedTool(tool.toolCode(), tool.toolName()))
                .toList();
    }

    private JsonNode routingNode(JsonNode metadata) {
        return metadata == null ? MissingNode.getInstance() : metadata.path("routing");
    }

    private JsonNode firstPresent(JsonNode node, String primaryField, String compatibilityField) {
        JsonNode primaryNode = node.path(primaryField);
        if (!primaryNode.isMissingNode() && !primaryNode.isNull()) {
            return primaryNode;
        }
        return node.path(compatibilityField);
    }

    private String firstPresentText(JsonNode node, String primaryField, String compatibilityField) {
        JsonNode targetNode = firstPresent(node, primaryField, compatibilityField);
        if (targetNode.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : targetNode) {
                String value = compactText(item.asText(null));
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return String.join("，", values);
        }
        return compactText(targetNode.asText(null));
    }

    private boolean matchesAny(String normalizedText, JsonNode valueNode) {
        return countMatches(normalizedText, valueNode, 1) > 0;
    }

    private int countMatches(String normalizedText, JsonNode valueNode, int weight) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return 0;
        }
        if (valueNode.isArray()) {
            int score = 0;
            for (JsonNode item : valueNode) {
                score += countMatches(normalizedText, item.asText(null), weight);
            }
            return score;
        }
        return countMatches(normalizedText, valueNode.asText(null), weight);
    }

    private int countMatches(String normalizedText, String rawText, int weight) {
        if (rawText == null || rawText.isBlank()) {
            return 0;
        }
        String[] tokens = rawText.toLowerCase(Locale.ROOT).split("[,，;；\\n]+");
        int score = 0;
        for (String token : tokens) {
            String keyword = token.trim();
            if (!keyword.isEmpty() && normalizedText.contains(keyword)) {
                score += weight;
            }
        }
        return score;
    }

    private String compactText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').trim();
    }

    private boolean containsApprovalKeyword(String text) {
        return text.contains("审批")
                || text.contains("审核")
                || text.contains("确认")
                || text.contains("approve");
    }

    private boolean containsWorkflowIntent(String text) {
        return text.contains("流程")
                || text.contains("workflow")
                || text.contains("节点")
                || text.contains("审批")
                || text.contains("审核");
    }

    private ExecutionDecision resolveFollowUpRefinementDecision(ExecutionContext executionContext) {
        if (!hasFollowUpRefinementIntent(executionContext)) {
            return null;
        }
        RegistryAgentWorkflow refinementWorkflow = executionContext.availableWorkflows().stream()
                .map(workflow -> Map.entry(workflow, scoreContinuationWorkflow(workflow)))
                .filter(entry -> entry.getValue() > 0)
                .max(Comparator.<Map.Entry<RegistryAgentWorkflow, Integer>>comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (refinementWorkflow != null) {
            return new ExecutionDecision(
                    "workflow",
                    "检测到用户正在基于上一轮结果继续细化或调整，优先进入可继续规划/重排的 Workflow",
                    List.of(),
                    List.of(new ExecutionDecision.SelectedWorkflow(refinementWorkflow.workflowId(), refinementWorkflow.workflowName()))
            );
        }
        List<RegistryAgentTool> matchedTools = resolveMatchedTools(executionContext);
        if (!matchedTools.isEmpty()) {
            return new ExecutionDecision(
                    "tool",
                    "检测到用户正在基于上一轮结果继续细化或调整，回退为 Tool 组合继续补充事实",
                    buildSelectedTools(matchedTools),
                    List.of()
            );
        }
        return null;
    }

    private boolean hasFollowUpRefinementIntent(ExecutionContext executionContext) {
        String userMessage = safeLowerText(executionContext.userMessage());
        if (userMessage.isBlank()) {
            return false;
        }
        boolean hasPreviousContext = (executionContext.recentTaskMemoryText() != null && !executionContext.recentTaskMemoryText().isBlank())
                || (executionContext.conversationMemoryText() != null && !executionContext.conversationMemoryText().isBlank())
                || (executionContext.recentConversationMessages() != null && !executionContext.recentConversationMessages().isEmpty());
        if (!hasPreviousContext) {
            return false;
        }
        return containsAny(userMessage,
                "继续", "再", "基于上面", "基于前面", "按刚才", "按之前", "上一版", "这版",
                "调整", "修改", "优化", "重排", "重写", "重做", "细化", "补充", "保留",
                "删掉", "去掉", "改成", "换成", "更稳", "更保守", "更详细", "简化",
                "keep", "revise", "refine", "adjust", "update", "reorder", "rewrite", "continue");
    }

    private int scoreContinuationWorkflow(RegistryAgentWorkflow workflow) {
        String corpus = String.join("\n",
                safeLowerText(workflow.workflowName()),
                safeLowerText(workflow.workflowCode()),
                safeLowerText(workflow.workflowType()),
                safeLowerText(workflow.workflowCategory()),
                safeLowerText(workflow.description()));
        int score = 0;
        if (containsAny(corpus, "规划", "重规划", "重排", "调整", "反馈", "优化", "方案", "plan", "replan", "refine", "revise", "feedback")) {
            score += 5;
        }
        if (containsAny(corpus, "回复", "输出", "总结", "reply", "response", "summary")) {
            score += 2;
        }
        return score;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safeLowerText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String buildSystemPrompt(ExecutionContext executionContext) {
        return """
                你是 Agent 执行决策器。
                只允许从输入给定的 Tool 和 Workflow 中做选择，不能编造不存在的编码或 ID。
                输出必须是严格 JSON，不要 Markdown，不要解释，不要输出多余文本。
                JSON 字段固定为：
                decisionType
                reason
                selectedTools
                selectedWorkflows
                其中 selectedTools 为数组，每项包含：
                toolCode
                toolName
                其中 selectedWorkflows 为数组，每项包含：
                workflowId
                workflowName
                如需兼容旧格式，也允许返回 targetToolCode 和 targetWorkflowId。
                """
                + "\n\n时间上下文：\n" + nullSafe(executionContext.currentTimeText())
                + "\n\n决策 Prompt：\n" + executionContext.decisionPromptContext().promptContent();
    }

    private PromptTracePackage buildDecisionPromptTracePackage(ExecutionContext executionContext) throws JsonProcessingException {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("decision"),
                PromptContextBudgetService.PromptStage.DECISION
        );
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        EvalFeedbackService.EvalFeedbackSummary evalFeedback = evalFeedbackService.summarizeAgentFeedback(executionContext.agentContext().agentId());
        List<Map<String, Object>> tools = executionContext.availableTools().stream()
                .map(tool -> Map.of(
                        "toolCode", tool.toolCode(),
                        "toolName", nullSafe(tool.toolName()),
                        "toolType", nullSafe(tool.toolType()),
                        "toolCategory", nullSafe(tool.toolCategory()),
                        "providerType", nullSafe(tool.providerType()),
                        "transportType", nullSafe(tool.transportType()),
                        "description", nullSafe(tool.description()),
                        "inputSchema", tool.inputSchema() == null ? Map.of() : tool.inputSchema(),
                        "outputSchema", tool.outputSchema() == null ? Map.of() : tool.outputSchema(),
                        "routing", tool.metadata() == null ? Map.of() : tool.metadata().path("routing")
                ))
                .toList();
        List<Map<String, Object>> workflows = executionContext.availableWorkflows().stream()
                .map(workflow -> Map.of(
                        "workflowId", workflow.workflowId(),
                        "workflowCode", nullSafe(workflow.workflowCode()),
                        "workflowName", nullSafe(workflow.workflowName()),
                        "workflowType", nullSafe(workflow.workflowType()),
                        "workflowCategory", nullSafe(workflow.workflowCategory()),
                        "description", nullSafe(workflow.description()),
                        "routing", workflow.metadata() == null ? Map.of() : workflow.metadata().path("routing")
                ))
                .toList();
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("userMessage", compacted.conversationAwareUserMessage());
        input.put("protectedFacts", compacted.protectedFactsPayload());
        input.put("conversationState", executionContext.conversationStatePayload());
        input.put("conversationStateText", compacted.conversationStateText());
        input.put("conversationMemory", compacted.conversationMemory());
        input.put("userMemory", compacted.userMemory());
        input.put("recentTaskMemory", compacted.recentTaskMemory());
        input.put("availableTools", tools);
        input.put("availableWorkflows", workflows);
        input.put("evalFeedback", Map.of(
                        "latestAverageScore", evalFeedback.latestAverageScore() == null ? "" : evalFeedback.latestAverageScore(),
                        "latestPassRate", evalFeedback.latestPassRate() == null ? "" : evalFeedback.latestPassRate(),
                        "latestRunCode", nullSafe(evalFeedback.latestRunCode()),
                        "preferredToolCodes", evalFeedback.preferredToolCodes()
                ));
        LinkedHashMap<String, Object> enrichedInput = new LinkedHashMap<>(input);
        enrichedInput.put("currentDate", executionContext.timeContext() == null ? "" : nullSafe(executionContext.timeContext().currentDate()));
        enrichedInput.put("currentDateTime", executionContext.timeContext() == null ? "" : nullSafe(executionContext.timeContext().localDateTime()));
        enrichedInput.put("zoneId", executionContext.timeContext() == null ? "" : nullSafe(executionContext.timeContext().zoneId()));
        enrichedInput.put("recentConversationSummary", compacted.recentConversationSummary());
        enrichedInput.put("recentUserMessages", compacted.recentUserMessages());
        enrichedInput.put("contextCompaction", compacted.metadata());
        String systemPrompt = buildSystemPrompt(executionContext);
        String userPrompt = promptContextBudgetService.truncateToChars(objectMapper.writeValueAsString(enrichedInput), budget.promptChars());
        Map<String, Object> traceMetadata = contextCompactionService.enrichPromptAssemblyMetadata(
                compacted.metadata(),
                systemPrompt,
                userPrompt
        );
        log.info("上下文压缩[decision] taskId={}, traceId={}, compactedChars={}, savedChars={}, summarized={}",
                executionContext.taskContext() == null ? null : executionContext.taskContext().taskId(),
                executionContext.taskContext() == null ? null : executionContext.taskContext().traceId(),
                compacted.metadata().get("compactedChars"),
                compacted.metadata().get("savedChars"),
                compacted.metadata().get("summarized"));
        return new PromptTracePackage(systemPrompt, userPrompt, traceMetadata);
    }

    private String buildRoutingText(ExecutionContext executionContext) {
        StringBuilder builder = new StringBuilder();
        builder.append(nullSafe(executionContext.userMessage()).toLowerCase(Locale.ROOT));
        String stateText = nullSafe(executionContext.conversationStatePromptText()).toLowerCase(Locale.ROOT);
        if (!stateText.isBlank()) {
            builder.append("\n").append(stateText);
        }
        String conversationMemory = nullSafe(executionContext.conversationMemoryText()).toLowerCase(Locale.ROOT);
        if (!conversationMemory.isBlank()) {
            builder.append("\n").append(conversationMemory);
        }
        String recentTaskMemory = nullSafe(executionContext.recentTaskMemoryText()).toLowerCase(Locale.ROOT);
        if (!recentTaskMemory.isBlank()) {
            builder.append("\n").append(recentTaskMemory);
        }
        if (executionContext.recentConversationMessages() != null) {
            for (ExecutionContext.ConversationMessageContext item : executionContext.recentConversationMessages()) {
                if (item == null || item.content() == null || item.content().isBlank()) {
                    continue;
                }
                builder.append("\n").append(item.content().toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private LlmDecisionResponse parseLlmDecisionResponse(String responseText) throws JsonProcessingException {
        String normalizedText = runtimeLlmGatewayService.sanitizeJsonText(responseText);
        return objectMapper.readValue(normalizedText, LlmDecisionResponse.class);
    }

    private ExecutionDecision toExecutionDecision(LlmDecisionResponse response, ExecutionContext executionContext) {
        String decisionType = compactText(response.getDecisionType()).toLowerCase(Locale.ROOT);
        List<ExecutionDecision.SelectedTool> selectedTools = resolveSelectedTools(response, executionContext, decisionType);
        List<ExecutionDecision.SelectedWorkflow> selectedWorkflows = resolveSelectedWorkflows(response, executionContext, decisionType);
        if (decisionType.isBlank()) {
            decisionType = inferDecisionType(selectedTools, selectedWorkflows);
        }
        if (decisionType.isBlank()) {
            return null;
        }
        return new ExecutionDecision(decisionType, compactText(response.getReason()), selectedTools, selectedWorkflows);
    }

    private String inferDecisionType(List<ExecutionDecision.SelectedTool> selectedTools,
                                     List<ExecutionDecision.SelectedWorkflow> selectedWorkflows) {
        boolean hasTools = selectedTools != null && !selectedTools.isEmpty();
        boolean hasWorkflows = selectedWorkflows != null && !selectedWorkflows.isEmpty();
        if (hasTools && hasWorkflows) {
            return "hybrid";
        }
        if (hasWorkflows) {
            return "workflow";
        }
        if (hasTools) {
            return "tool";
        }
        return "";
    }

    private List<ExecutionDecision.SelectedTool> resolveSelectedTools(LlmDecisionResponse response,
                                                                      ExecutionContext executionContext,
                                                                      String decisionType) {
        if (response.getSelectedTools() != null && !response.getSelectedTools().isEmpty()) {
            return response.getSelectedTools().stream()
                    .map(item -> toSelectedTool(item, executionContext))
                    .toList();
        }
        if ("hybrid".equals(decisionType)) {
            RegistryAgentTool tool = executionContext.availableTools().stream()
                    .filter(item -> item.toolCode().equals(response.getTargetToolCode()))
                    .findFirst()
                    .orElse(null);
            if (tool == null) {
                throw new IllegalStateException("LLM 决策返回的 Tool 不存在");
            }
            return List.of(new ExecutionDecision.SelectedTool(tool.toolCode(), tool.toolName()));
        }
        if ("tool".equals(decisionType)) {
            RegistryAgentTool tool = executionContext.availableTools().stream()
                    .filter(item -> item.toolCode().equals(response.getTargetToolCode()))
                    .findFirst()
                    .orElse(null);
            if (tool == null) {
                throw new IllegalStateException("LLM 决策返回的 Tool 不存在");
            }
            return List.of(new ExecutionDecision.SelectedTool(tool.toolCode(), tool.toolName()));
        }
        return List.of();
    }

    private List<ExecutionDecision.SelectedWorkflow> resolveSelectedWorkflows(LlmDecisionResponse response,
                                                                              ExecutionContext executionContext,
                                                                              String decisionType) {
        if (response.getSelectedWorkflows() != null && !response.getSelectedWorkflows().isEmpty()) {
            return response.getSelectedWorkflows().stream()
                    .map(item -> toSelectedWorkflow(item, executionContext))
                    .toList();
        }
        if ("hybrid".equals(decisionType) || "workflow".equals(decisionType)) {
            RegistryAgentWorkflow workflow = executionContext.availableWorkflows().stream()
                    .filter(item -> item.workflowId().equals(response.getTargetWorkflowId()))
                    .findFirst()
                    .orElse(null);
            if (workflow == null) {
                throw new IllegalStateException("LLM 决策返回的 Workflow 不存在");
            }
            return List.of(new ExecutionDecision.SelectedWorkflow(workflow.workflowId(), workflow.workflowName()));
        }
        return List.of();
    }

    private ExecutionDecision.SelectedTool toSelectedTool(LlmDecisionToolResponse item, ExecutionContext executionContext) {
        RegistryAgentTool tool = executionContext.availableTools().stream()
                .filter(candidate -> candidate.toolCode().equals(item.getToolCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("LLM 决策返回的 Tool 不存在"));
        String toolName = compactText(item.getToolName()).isEmpty() ? tool.toolName() : compactText(item.getToolName());
        return new ExecutionDecision.SelectedTool(tool.toolCode(), toolName);
    }

    private ExecutionDecision.SelectedWorkflow toSelectedWorkflow(LlmDecisionWorkflowResponse item,
                                                                  ExecutionContext executionContext) {
        RegistryAgentWorkflow workflow = executionContext.availableWorkflows().stream()
                .filter(candidate -> candidate.workflowId().equals(item.getWorkflowId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("LLM 决策返回的 Workflow 不存在"));
        String workflowName = compactText(item.getWorkflowName()).isEmpty()
                ? workflow.workflowName()
                : compactText(item.getWorkflowName());
        return new ExecutionDecision.SelectedWorkflow(workflow.workflowId(), workflowName);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record PromptTracePackage(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> compactionMetadata
    ) {
    }

    /**
     * LLM 决策返回结构。
     */
    public static class LlmDecisionResponse {

        private String decisionType;
        private String targetToolCode;
        private Long targetWorkflowId;
        private String reason;
        private List<LlmDecisionToolResponse> selectedTools;
        private List<LlmDecisionWorkflowResponse> selectedWorkflows;

        public String getDecisionType() {
            return decisionType;
        }

        public void setDecisionType(String decisionType) {
            this.decisionType = decisionType;
        }

        public String getTargetToolCode() {
            return targetToolCode;
        }

        public void setTargetToolCode(String targetToolCode) {
            this.targetToolCode = targetToolCode;
        }

        public Long getTargetWorkflowId() {
            return targetWorkflowId;
        }

        public void setTargetWorkflowId(Long targetWorkflowId) {
            this.targetWorkflowId = targetWorkflowId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public List<LlmDecisionToolResponse> getSelectedTools() {
            return selectedTools;
        }

        public void setSelectedTools(List<LlmDecisionToolResponse> selectedTools) {
            this.selectedTools = selectedTools;
        }

        public List<LlmDecisionWorkflowResponse> getSelectedWorkflows() {
            return selectedWorkflows;
        }

        public void setSelectedWorkflows(List<LlmDecisionWorkflowResponse> selectedWorkflows) {
            this.selectedWorkflows = selectedWorkflows;
        }
    }

    /**
     * LLM 决策 Tool 返回结构。
     */
    public static class LlmDecisionToolResponse {

        private String toolCode;
        private String toolName;

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }
    }

    /**
     * LLM 决策 Workflow 返回结构。
     */
    public static class LlmDecisionWorkflowResponse {

        private Long workflowId;
        private String workflowName;

        public Long getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(Long workflowId) {
            this.workflowId = workflowId;
        }

        public String getWorkflowName() {
            return workflowName;
        }

        public void setWorkflowName(String workflowName) {
            this.workflowName = workflowName;
        }
    }
}
