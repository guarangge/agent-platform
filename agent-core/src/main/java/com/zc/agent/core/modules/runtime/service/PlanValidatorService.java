package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计划校验服务。
 * 对 LLM 输出的计划做结构、资源引用和执行兼容性校验。
 */
@Service
public class PlanValidatorService {

    private static final Set<String> FAILURE_POLICIES = Set.of("fail_fast", "continue", "replan");
    private static final int MAX_STEP_COUNT = 12;
    private final StepDataContractService stepDataContractService;

    public PlanValidatorService(StepDataContractService stepDataContractService) {
        this.stepDataContractService = stepDataContractService;
    }

    /**
     * 校验并规范化计划。
     *
     * @param executionPlan    原始计划
     * @param executionContext 执行上下文
     * @return 规范化后的计划
     */
    public ExecutionPlan validateAndNormalize(ExecutionPlan executionPlan, ExecutionContext executionContext) {
        Objects.requireNonNull(executionPlan, "executionPlan 不能为空");
        if (executionPlan.steps() == null || executionPlan.steps().isEmpty()) {
            throw new IllegalStateException("计划步骤不能为空");
        }
        if (executionPlan.steps().size() > MAX_STEP_COUNT) {
            throw new IllegalStateException("计划步骤数超过上限: " + MAX_STEP_COUNT);
        }
        Set<String> toolCodes = executionContext.availableTools().stream()
                .map(item -> item.toolCode())
                .collect(Collectors.toSet());
        Set<Long> workflowIds = executionContext.availableWorkflows().stream()
                .map(item -> item.workflowId())
                .collect(Collectors.toSet());
        Set<Long> delegateAgentIds = executionContext.availableDelegates().stream()
                .map(item -> item.agentId())
                .collect(Collectors.toSet());
        List<ExecutionPlan.ExecutionPlanStep> normalizedSteps = new ArrayList<>();
        Set<String> stepIds = new LinkedHashSet<>();
        for (int index = 0; index < executionPlan.steps().size(); index++) {
            ExecutionPlan.ExecutionPlanStep step = executionPlan.steps().get(index);
            int expectedStepNo = index + 1;
            String stepType = normalizeText(step.stepType()).toLowerCase(Locale.ROOT);
            try {
                ExecutionStepType.requirePlanType(stepType);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("当前执行器暂不支持的步骤类型: " + stepType);
            }
            String stepId = normalizeText(step.stepId());
            if (stepId.isBlank()) {
                stepId = stepType + "_" + expectedStepNo;
            }
            if (!stepIds.add(stepId)) {
                throw new IllegalStateException("步骤 ID 重复: " + stepId);
            }
            List<String> dependsOnStepIds = normalizeDependsOn(step.dependsOnStepIds(), stepIds, stepId);
            if ("tool".equals(stepType)) {
                if (normalizeText(step.targetToolCode()).isBlank()) {
                    throw new IllegalStateException("Tool 步骤缺少 targetToolCode");
                }
                if (!toolCodes.contains(step.targetToolCode())) {
                    throw new IllegalStateException("计划引用了不存在的 Tool: " + step.targetToolCode());
                }
            }
            if ("workflow".equals(stepType)) {
                if (step.targetWorkflowId() == null) {
                    throw new IllegalStateException("Workflow 步骤缺少 targetWorkflowId");
                }
                if (!workflowIds.contains(step.targetWorkflowId())) {
                    throw new IllegalStateException("计划引用了不存在的 Workflow: " + step.targetWorkflowId());
                }
            }
            if ("condition".equals(stepType)) {
                validateConditionStep(step);
            }
            if ("approval".equals(stepType)) {
                validateApprovalStep(step);
            }
            if ("handoff".equals(stepType)) {
                validateHandoffStep(step, delegateAgentIds);
            }
            if ("reflection".equals(stepType)) {
                validateReflectionStep(step);
            }
            if ("clarify".equals(stepType)) {
                validateClarifyStep(step);
            }
            if ("judge".equals(stepType)) {
                validateJudgeStep(step);
            }
            if ("wait".equals(stepType)) {
                validateWaitStep(step);
            }
            if ("memory_write".equals(stepType)) {
                validateMemoryWriteStep(step);
            }
            if ("parallel".equals(stepType)) {
                validateParallelStep(step);
            }
            if ("join".equals(stepType)) {
                validateJoinStep(step);
            }
            String failurePolicy = normalizeFailurePolicy(step.failurePolicy());
            boolean replannable = step.replannable() == null || step.replannable();
            if ("reflection".equals(stepType)) {
                failurePolicy = "replan";
                replannable = true;
            }
            // Workflow 产出仍会经过 Observation；允许重规划的步骤不能被 fail_fast 提前截断。
            if ("workflow".equals(stepType) && replannable) {
                failurePolicy = "replan";
            }
            normalizedSteps.add(new ExecutionPlan.ExecutionPlanStep(
                    expectedStepNo,
                    stepId,
                    stepType,
                    blankToDefault(step.stepName(), "步骤" + expectedStepNo),
                    blankToDefault(step.objective(), blankToDefault(step.stepName(), "完成当前步骤")),
                    dependsOnStepIds,
                    "tool".equals(stepType) ? step.targetToolCode() : null,
                    "workflow".equals(stepType) ? step.targetWorkflowId() : null,
                    step.inputs() == null ? java.util.Map.of() : step.inputs(),
                    blankToDefault(step.expectedOutput(), "返回步骤结果"),
                    failurePolicy,
                    replannable,
                    blankToDefault(step.status(), "draft"),
                    blankToDefault(step.description(), blankToDefault(step.objective(), ""))
            ));
        }
        normalizedSteps = removeRedundantReplyAfterUserReplyWorkflow(normalizedSteps, executionContext);
        normalizedSteps = ensureTerminalReply(executionPlan, normalizedSteps, executionContext);
        validateNoCycle(normalizedSteps);
        stepDataContractService.validatePlanContracts(normalizedSteps);
        return new ExecutionPlan(
                blankToDefault(executionPlan.planId(), "PLAN_DRAFT"),
                normalizePlanType(blankToDefault(executionPlan.planType(), "tool_sequence"), normalizedSteps),
                blankToDefault(executionPlan.goal(), executionContext.userMessage()),
                blankToDefault(executionPlan.status(), "draft"),
                executionPlan.assumptions() == null ? List.of() : executionPlan.assumptions(),
                executionPlan.constraints() == null ? List.of() : executionPlan.constraints(),
                executionPlan.risks() == null ? List.of() : executionPlan.risks(),
                normalizedSteps
        );
    }

    private List<ExecutionPlan.ExecutionPlanStep> removeRedundantReplyAfterUserReplyWorkflow(
            List<ExecutionPlan.ExecutionPlanStep> normalizedSteps,
            ExecutionContext executionContext) {
        if (normalizedSteps.size() < 2) return normalizedSteps;
        ExecutionPlan.ExecutionPlanStep replyStep = normalizedSteps.get(normalizedSteps.size() - 1);
        ExecutionPlan.ExecutionPlanStep workflowStep = normalizedSteps.get(normalizedSteps.size() - 2);
        if (!"reply".equals(replyStep.stepType())
                || Boolean.TRUE.equals(replyStep.inputs().get("forceResponseRewrite"))
                || !isUserReplyWorkflowStep(workflowStep, executionContext)) {
            return normalizedSteps;
        }
        return new ArrayList<>(normalizedSteps.subList(0, normalizedSteps.size() - 1));
    }

    private List<ExecutionPlan.ExecutionPlanStep> ensureTerminalReply(ExecutionPlan executionPlan,
                                                                      List<ExecutionPlan.ExecutionPlanStep> normalizedSteps,
                                                                      ExecutionContext executionContext) {
        if (normalizedSteps.isEmpty()) {
            return normalizedSteps;
        }
        ExecutionPlan.ExecutionPlanStep lastStep = normalizedSteps.get(normalizedSteps.size() - 1);
        if (ExecutionStepType.requirePlanType(lastStep.stepType()).terminal()) {
            return normalizedSteps;
        }
        if (isUserReplyWorkflowStep(lastStep, executionContext)) {
            return normalizedSteps;
        }
        boolean hasFactProducingStep = normalizedSteps.stream()
                .anyMatch(step -> ExecutionStepType.requirePlanType(step.stepType()).factProducing());
        if (!hasFactProducingStep) {
            return normalizedSteps;
        }
        if (normalizedSteps.size() >= MAX_STEP_COUNT) {
            throw new IllegalStateException("多步计划缺少最终 reply 收口，且步骤数已达上限: " + MAX_STEP_COUNT);
        }
        ArrayList<ExecutionPlan.ExecutionPlanStep> enrichedSteps = new ArrayList<>(normalizedSteps);
        int replyStepNo = enrichedSteps.size() + 1;
        enrichedSteps.add(new ExecutionPlan.ExecutionPlanStep(
                replyStepNo,
                "reply_" + replyStepNo,
                "reply",
                "输出最终方案",
                "基于前序步骤结果向用户输出最终回复",
                List.of(lastStep.stepId()),
                null,
                null,
                java.util.Map.of(),
                "输出最终回复",
                "replan",
                Boolean.TRUE,
                "draft",
                "自动补充最终 reply 收口，避免任务成功但没有用户答复"
        ));
        return enrichedSteps;
    }

    private boolean isUserReplyWorkflowStep(ExecutionPlan.ExecutionPlanStep step,
                                            ExecutionContext executionContext) {
        if (!"workflow".equals(step.stepType()) || step.targetWorkflowId() == null) return false;
        return executionContext.availableWorkflows().stream()
                .filter(workflow -> step.targetWorkflowId().equals(workflow.workflowId()))
                .map(RegistryAgentWorkflow::metadata)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.path("routing").path("outputMode").asText(""))
                .anyMatch("user_reply"::equalsIgnoreCase);
    }

    private String normalizePlanType(String rawPlanType, List<ExecutionPlan.ExecutionPlanStep> steps) {
        String normalizedPlanType = normalizeText(rawPlanType).toLowerCase(Locale.ROOT);
        boolean hasTool = steps.stream().anyMatch(step -> "tool".equals(step.stepType()) || "parallel".equals(step.stepType()) || "join".equals(step.stepType()));
        boolean hasWorkflow = steps.stream().anyMatch(step -> "workflow".equals(step.stepType()));
        boolean replyOnly = steps.stream().allMatch(step -> "reply".equals(step.stepType()));
        if (replyOnly) {
            return "direct_reply";
        }
        if (hasTool && hasWorkflow) {
            return "hybrid";
        }
        if (hasWorkflow) {
            return "workflow";
        }
        if (hasTool) {
            long toolStepCount = steps.stream().filter(step -> "tool".equals(step.stepType()) || "parallel".equals(step.stepType()) || "join".equals(step.stepType())).count();
            return toolStepCount > 1 ? "tool_sequence" : "tool";
        }
        if (normalizedPlanType.isBlank()) {
            return "direct_reply";
        }
        return normalizedPlanType;
    }

    private void validateConditionStep(ExecutionPlan.ExecutionPlanStep step) {
        if (step.inputs() == null || step.inputs().isEmpty()) {
            throw new IllegalStateException("condition 步骤缺少 inputs 配置");
        }
        String mode = normalizeText(String.valueOf(step.inputs().getOrDefault("mode", ""))).toLowerCase(Locale.ROOT);
        boolean hasKeywords = step.inputs().containsKey("keywords") || step.inputs().containsKey("keyword");
        boolean hasToolRule = step.inputs().containsKey("availableToolCode");
        boolean hasExpression = step.inputs().containsKey("expression")
                && !normalizeText(String.valueOf(step.inputs().get("expression"))).isBlank();
        boolean hasLlmRule = "llm".equals(mode)
                || (step.inputs().containsKey("llmCondition")
                && !normalizeText(String.valueOf(step.inputs().get("llmCondition"))).isBlank())
                || (step.inputs().containsKey("semanticRule")
                && !normalizeText(String.valueOf(step.inputs().get("semanticRule"))).isBlank());
        if (!hasKeywords && !hasToolRule && !hasExpression && !hasLlmRule) {
            throw new IllegalStateException("condition 步骤缺少有效判断规则");
        }
    }

    private void validateApprovalStep(ExecutionPlan.ExecutionPlanStep step) {
        if (normalizeText(step.stepName()).isBlank()) {
            throw new IllegalStateException("approval 步骤缺少 stepName");
        }
    }

    private void validateHandoffStep(ExecutionPlan.ExecutionPlanStep step, Set<Long> delegateAgentIds) {
        if (step.inputs() == null || step.inputs().isEmpty()) {
            throw new IllegalStateException("handoff 步骤缺少 inputs 配置");
        }
        Object delegateAgentIdValue = step.inputs().get("delegateAgentId");
        Object requiredCapabilities = step.inputs().get("requiredCapabilities");
        boolean hasCapabilities = requiredCapabilities instanceof java.util.Collection<?> collection && !collection.isEmpty()
                || requiredCapabilities instanceof String text && !text.isBlank();
        Long delegateAgentId = null;
        if (delegateAgentIdValue instanceof Number number) {
            delegateAgentId = number.longValue();
        } else if (delegateAgentIdValue instanceof String text && !text.isBlank()) {
            try {
                delegateAgentId = Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("handoff 步骤的 delegateAgentId 不是合法数字");
            }
        }
        if (delegateAgentId == null && !hasCapabilities) {
            throw new IllegalStateException("handoff 步骤必须提供 delegateAgentId 或 requiredCapabilities");
        }
        if (delegateAgentId != null && !delegateAgentIds.contains(delegateAgentId)) {
            throw new IllegalStateException("handoff 步骤引用了当前不可委派的 Agent: " + delegateAgentId);
        }
    }

    private void validateReflectionStep(ExecutionPlan.ExecutionPlanStep step) {
        if (normalizeText(step.objective()).isBlank() && normalizeText(step.stepName()).isBlank()) {
            throw new IllegalStateException("reflection 步骤缺少目标描述");
        }
    }

    private void validateClarifyStep(ExecutionPlan.ExecutionPlanStep step) {
        if (normalizeText(step.objective()).isBlank() && normalizeText(step.stepName()).isBlank()) {
            throw new IllegalStateException("clarify 步骤缺少目标描述");
        }
    }

    private void validateJudgeStep(ExecutionPlan.ExecutionPlanStep step) {
        if (normalizeText(step.objective()).isBlank() && normalizeText(step.stepName()).isBlank()) {
            throw new IllegalStateException("judge 步骤缺少目标描述");
        }
    }

    private void validateWaitStep(ExecutionPlan.ExecutionPlanStep step) {
        if (step.inputs() == null || (!step.inputs().containsKey("waitMillis")
                && !step.inputs().containsKey("waitSeconds")
                && !List.of("event", "webhook").contains(String.valueOf(step.inputs().get("waitType"))))) {
            throw new IllegalStateException("wait 步骤缺少定时等待参数或 event/webhook waitType");
        }
    }

    private void validateMemoryWriteStep(ExecutionPlan.ExecutionPlanStep step) {
        if (step.inputs() == null || step.inputs().isEmpty()) {
            throw new IllegalStateException("memory_write 步骤缺少 inputs 配置");
        }
    }

    private void validateParallelStep(ExecutionPlan.ExecutionPlanStep step) {
        if (step.inputs() == null || !step.inputs().containsKey("parallelToolCodes")) {
            throw new IllegalStateException("parallel 步骤缺少 parallelToolCodes");
        }
    }

    private void validateJoinStep(ExecutionPlan.ExecutionPlanStep step) {
        if (normalizeText(step.objective()).isBlank() && normalizeText(step.stepName()).isBlank()) {
            throw new IllegalStateException("join 步骤缺少目标描述");
        }
    }

    private List<String> normalizeDependsOn(List<String> dependsOnStepIds,
                                            Set<String> knownStepIds,
                                            String currentStepId) {
        if (dependsOnStepIds == null || dependsOnStepIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String dependsOnStepId : dependsOnStepIds) {
            String normalizedId = normalizeText(dependsOnStepId);
            if (normalizedId.isBlank()) {
                continue;
            }
            if (currentStepId.equals(normalizedId)) {
                throw new IllegalStateException("步骤不能依赖自己: " + currentStepId);
            }
            if (!knownStepIds.contains(normalizedId)) {
                throw new IllegalStateException("步骤依赖了尚未定义的前置步骤: " + normalizedId);
            }
            normalized.add(normalizedId);
        }
        return normalized;
    }

    private void validateNoCycle(List<ExecutionPlan.ExecutionPlanStep> steps) {
        Set<String> visited = new HashSet<>();
        Set<String> path = new HashSet<>();
        for (ExecutionPlan.ExecutionPlanStep step : steps) {
            detectCycle(step.stepId(), steps, visited, path);
        }
    }

    private void detectCycle(String stepId,
                             List<ExecutionPlan.ExecutionPlanStep> steps,
                             Set<String> visited,
                             Set<String> path) {
        if (visited.contains(stepId)) {
            return;
        }
        if (!path.add(stepId)) {
            throw new IllegalStateException("计划存在循环依赖: " + stepId);
        }
        ExecutionPlan.ExecutionPlanStep step = steps.stream()
                .filter(item -> item.stepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("步骤不存在: " + stepId));
        for (String dependsOnStepId : step.dependsOnStepIds()) {
            detectCycle(dependsOnStepId, steps, visited, path);
        }
        path.remove(stepId);
        visited.add(stepId);
    }

    private String normalizeFailurePolicy(String failurePolicy) {
        String normalized = normalizeText(failurePolicy).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "fail_fast";
        }
        if (!FAILURE_POLICIES.contains(normalized)) {
            throw new IllegalStateException("不支持的 failurePolicy: " + failurePolicy);
        }
        return normalized;
    }

    private String blankToDefault(String value, String defaultValue) {
        return normalizeText(value).isBlank() ? defaultValue : normalizeText(value);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
