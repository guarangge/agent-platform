package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一执行输入裁剪策略。
 * 避免主链路、Workflow 链路分别拼装执行输入，导致长度控制不一致。
 */
@Service
public class ExecutionInputPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionInputPolicyService.class);
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionService contextCompactionService;

    public ExecutionInputPolicyService(PromptContextBudgetService promptContextBudgetService,
                                       ContextCompactionService contextCompactionService) {
        this.promptContextBudgetService = promptContextBudgetService;
        this.contextCompactionService = contextCompactionService;
    }

    /**
     * 为计划步骤构建精简执行输入。
     */
    public String buildPlanStepExecutionInput(ExecutionContext executionContext,
                                              ExecutionPlan.ExecutionPlanStep step,
                                              List<String> previousSummaries) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"),
                PromptContextBudgetService.PromptStage.EXECUTION_INPUT
        );
        int maxLength = resolveStepInputLimit(executionContext, step);
        return buildCompactExecutionInput(
                buildConversationAwareUserInput(executionContext, budget),
                previousSummaries,
                maxLength,
                budget.userMessageChars(),
                budget.previousSummaryChars()
        );
    }

    /**
     * 对计划步骤执行输入做最终长度钳制。
     */
    public String clampPlanStepExecutionInput(ExecutionContext executionContext,
                                              ExecutionPlan.ExecutionPlanStep step,
                                              String inputText) {
        int maxLength = resolveStepInputLimit(executionContext, step);
        String clamped = truncateText(inputText, maxLength);
        log.debug("执行输入钳制[planStep] stepType={}, stepId={}, targetToolCode={}, originalLength={}, clampedLength={}, maxLength={}",
                step.stepType(),
                step.stepId(),
                step.targetToolCode(),
                safeLength(inputText),
                safeLength(clamped),
                maxLength);
        return clamped;
    }

    public Map<String, Object> buildPlanStepInputObservation(ExecutionContext executionContext,
                                                             ExecutionPlan.ExecutionPlanStep step,
                                                             String rawInputText,
                                                             String clampedInputText) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"),
                PromptContextBudgetService.PromptStage.EXECUTION_INPUT
        );
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        int maxLength = resolveStepInputLimit(executionContext, step);
        LinkedHashMap<String, Object> observation = new LinkedHashMap<>();
        observation.put("stepType", step.stepType());
        observation.put("targetToolCode", step.targetToolCode());
        observation.put("rawInputChars", safeLength(rawInputText));
        observation.put("finalInputChars", safeLength(clampedInputText));
        observation.put("maxLength", maxLength);
        observation.put("clamped", safeLength(clampedInputText) < safeLength(rawInputText));
        observation.put("rawInputPreview", truncateText(rawInputText, 320));
        observation.put("finalInputPreview", truncateText(clampedInputText, 320));
        observation.put("contextCompaction", compacted.metadata());
        return observation;
    }

    /**
     * 为 Workflow 内部 Tool 节点构建精简执行输入。
     */
    public String buildWorkflowToolExecutionInput(ExecutionContext executionContext,
                                                  RegistryAgentTool targetTool,
                                                  List<String> previousSummaries) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"),
                PromptContextBudgetService.PromptStage.EXECUTION_INPUT
        );
        int maxLength = resolveToolInputLimit(targetTool);
        return buildCompactExecutionInput(
                buildConversationAwareUserInput(executionContext, budget),
                previousSummaries,
                maxLength,
                budget.userMessageChars(),
                budget.previousSummaryChars()
        );
    }

    /**
     * 对 Workflow Tool 节点执行输入做最终长度钳制。
     */
    public String clampWorkflowToolExecutionInput(RegistryAgentTool targetTool,
                                                  String inputText) {
        int maxLength = resolveToolInputLimit(targetTool);
        String clamped = truncateText(inputText, maxLength);
        log.info("执行输入钳制[workflowTool] toolCode={}, originalLength={}, clampedLength={}, maxLength={}",
                targetTool == null ? null : targetTool.toolCode(),
                safeLength(inputText),
                safeLength(clamped),
                maxLength);
        return clamped;
    }

    /**
     * 构建 Workflow 内部节点统一输入观测，字段与主计划步骤保持一致。
     */
    public Map<String, Object> buildWorkflowNodeInputObservation(ExecutionContext executionContext,
                                                                 String nodeType,
                                                                 RegistryAgentTool targetTool,
                                                                 String rawInputText,
                                                                 String finalInputText) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"), PromptContextBudgetService.PromptStage.EXECUTION_INPUT);
        int maxLength = targetTool == null ? budget.promptChars() : resolveToolInputLimit(targetTool);
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        LinkedHashMap<String, Object> observation = new LinkedHashMap<>();
        observation.put("scope", "workflow_node");
        observation.put("nodeType", nodeType);
        observation.put("targetToolCode", targetTool == null ? null : targetTool.toolCode());
        observation.put("rawInputChars", safeLength(rawInputText));
        observation.put("finalInputChars", safeLength(finalInputText));
        observation.put("maxLength", maxLength);
        observation.put("clamped", safeLength(finalInputText) < safeLength(rawInputText));
        observation.put("rawInputPreview", truncateText(rawInputText, 320));
        observation.put("finalInputPreview", truncateText(finalInputText, 320));
        observation.put("contextCompaction", compacted.metadata());
        return observation;
    }

    /**
     * 为 Workflow 条件/控制节点构建精简执行输入。
     */
    public String buildWorkflowControlExecutionInput(ExecutionContext executionContext,
                                                     List<String> previousSummaries) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"),
                PromptContextBudgetService.PromptStage.EXECUTION_INPUT
        );
        return buildCompactExecutionInput(
                buildConversationAwareUserInput(executionContext, budget),
                previousSummaries,
                budget.promptChars(),
                budget.userMessageChars(),
                budget.previousSummaryChars()
        );
    }

    private int resolveStepInputLimit(ExecutionContext executionContext,
                                      ExecutionPlan.ExecutionPlanStep step) {
        if ("tool".equals(step.stepType())) {
            RegistryAgentTool tool = executionContext.availableTools().stream()
                    .filter(item -> step.targetToolCode() != null && step.targetToolCode().equals(item.toolCode()))
                    .findFirst()
                    .orElse(null);
            return Math.min(
                    resolveToolInputLimit(tool),
                    promptContextBudgetService.resolveBudget(executionContext.modelForStage("planning"), PromptContextBudgetService.PromptStage.EXECUTION_INPUT).promptChars()
            );
        }
        if ("workflow".equals(step.stepType())) {
            return promptContextBudgetService.resolveBudget(executionContext.modelForStage("planning"), PromptContextBudgetService.PromptStage.EXECUTION_INPUT).promptChars();
        }
        return promptContextBudgetService.resolveBudget(executionContext.modelForStage("planning"), PromptContextBudgetService.PromptStage.EXECUTION_INPUT).promptChars();
    }

    private int resolveToolInputLimit(RegistryAgentTool tool) {
        int defaultLimit = promptContextBudgetService.resolveBudget(null, PromptContextBudgetService.PromptStage.EXECUTION_INPUT).promptChars();
        if (tool == null || tool.metadata() == null || tool.metadata().isMissingNode()) {
            return defaultLimit;
        }
        JsonNode guardrailsNode = tool.metadata().path("guardrails");
        int maxInputLength = guardrailsNode.path("maxInputLength").asInt(0);
        if (maxInputLength > 0) {
            return maxInputLength;
        }
        return defaultLimit;
    }

    private String buildConversationAwareUserInput(ExecutionContext executionContext,
                                                   PromptContextBudgetService.StageBudget budget) {
        if (executionContext == null) {
            return null;
        }
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        List<String> userTexts = new ArrayList<>(compacted.recentUserMessages());
        if (compacted.currentUserMessage() != null && !compacted.currentUserMessage().isBlank()) {
            userTexts.add(compacted.currentUserMessage().trim());
        }
        if (userTexts.isEmpty()) {
            return compacted.currentUserMessage();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentUserMessage", compacted.currentUserMessage() == null ? "" : compacted.currentUserMessage().trim());
        payload.put("protectedFacts", compacted.protectedFactsPayload());
        payload.put("conversationState", executionContext.conversationStatePayload());
        payload.put("conversationStateText", compacted.conversationStateText());
        payload.put("recentConversationSummary", compacted.recentConversationSummary());
        payload.put("recentUserMessages", userTexts);
        payload.put("contextCompaction", compacted.metadata());
        return payload.toString();
    }

    private String buildCompactExecutionInput(String userMessage,
                                              List<String> previousSummaries,
                                              int maxLength,
                                              int userMessageLimit,
                                              int summaryLimit) {
        StringBuilder builder = new StringBuilder();
        if (userMessage != null && !userMessage.isBlank()) {
            builder.append("用户问题：").append(truncateText(userMessage.trim(), userMessageLimit));
        }
        List<String> normalizedSummaries = previousSummaries == null
                ? List.of()
                : previousSummaries.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(3)
                .toList();
        if (!normalizedSummaries.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("前序结果：");
            for (int index = 0; index < normalizedSummaries.size(); index++) {
                builder.append("\n")
                        .append(index + 1)
                        .append(". ")
                        .append(truncateText(normalizedSummaries.get(index).trim(), summaryLimit));
            }
        }
        return promptContextBudgetService.truncateToChars(builder.toString(), maxLength);
    }

    private String truncateText(String text, int maxLength) {
        return promptContextBudgetService.truncateToChars(text, maxLength);
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
