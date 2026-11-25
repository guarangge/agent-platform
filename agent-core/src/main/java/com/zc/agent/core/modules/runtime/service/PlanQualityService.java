package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划质量评分服务。
 */
@Service
public class PlanQualityService {

    public PlanQualityResult evaluate(ExecutionPlan executionPlan, ExecutionContext executionContext) {
        int score = 100;
        List<String> issues = new ArrayList<>();
        List<ExecutionPlan.ExecutionPlanStep> steps = executionPlan.steps() == null ? List.of() : executionPlan.steps();
        if (executionPlan.goal() == null || executionPlan.goal().isBlank()) {
            score -= 15;
            issues.add("计划目标为空");
        }
        if (steps.isEmpty()) {
            score -= 50;
            issues.add("计划步骤为空");
        }
        if (steps.size() > 12) {
            score -= 10;
            issues.add("计划步骤超过 12 个，执行成本偏高");
        }
        if (!steps.isEmpty() && !producesFinalUserOutput(steps.get(steps.size() - 1), executionContext)) {
            score -= 25;
            issues.add("计划缺少最终用户收口步骤，可能出现任务成功但无最终回复");
        }
        if ("direct_reply".equalsIgnoreCase(executionPlan.planType())
                && steps.stream().anyMatch(step -> !"reply".equals(step.stepType()))) {
            score -= 20;
            issues.add("planType=direct_reply 与实际步骤结构不一致");
        }
        int missingObjectiveCount = 0;
        int unresolvedResourceCount = 0;
        int missingExpectedOutputCount = 0;
        for (ExecutionPlan.ExecutionPlanStep step : steps) {
            if (step.objective() == null || step.objective().isBlank()) {
                missingObjectiveCount++;
            }
            if (step.expectedOutput() == null || step.expectedOutput().isBlank()) {
                missingExpectedOutputCount++;
            }
            if ("tool".equals(step.stepType())
                    && (step.targetToolCode() == null || step.targetToolCode().isBlank()
                    || executionContext.availableTools().stream().noneMatch(item -> step.targetToolCode().equals(item.toolCode())))) {
                unresolvedResourceCount++;
            }
            if ("workflow".equals(step.stepType())
                    && (step.targetWorkflowId() == null
                    || executionContext.availableWorkflows().stream().noneMatch(item -> step.targetWorkflowId().equals(item.workflowId())))) {
                unresolvedResourceCount++;
            }
            if ("handoff".equals(step.stepType())) {
                Object delegateAgentId = step.inputs() == null ? null : step.inputs().get("delegateAgentId");
                boolean matched = executionContext.availableDelegates().stream()
                        .anyMatch(item -> String.valueOf(item.agentId()).equals(String.valueOf(delegateAgentId)));
                if (delegateAgentId == null || !matched) {
                    unresolvedResourceCount++;
                }
            }
        }
        if (missingObjectiveCount > 0) {
            score -= Math.min(20, missingObjectiveCount * 4);
            issues.add("存在 " + missingObjectiveCount + " 个步骤缺少 objective");
        }
        if (missingExpectedOutputCount > 0) {
            score -= Math.min(10, missingExpectedOutputCount * 2);
            issues.add("存在 " + missingExpectedOutputCount + " 个步骤缺少 expectedOutput");
        }
        if (unresolvedResourceCount > 0) {
            score -= Math.min(30, unresolvedResourceCount * 8);
            issues.add("存在 " + unresolvedResourceCount + " 个步骤资源未正确绑定");
        }
        score = Math.max(0, Math.min(100, score));
        return new PlanQualityResult(score, issues, buildSummary(score, issues, steps.size()));
    }

    private boolean producesFinalUserOutput(ExecutionPlan.ExecutionPlanStep step, ExecutionContext context) {
        if (ExecutionStepType.requirePlanType(step.stepType()).terminal()) {
            return true;
        }
        if (!"workflow".equals(step.stepType()) || step.targetWorkflowId() == null) {
            return false;
        }
        return context.availableWorkflows().stream()
                .filter(workflow -> step.targetWorkflowId().equals(workflow.workflowId()))
                .anyMatch(workflow -> "user_reply".equalsIgnoreCase(workflow.metadata() == null
                        ? null : workflow.metadata().path("routing").path("outputMode").asText(null)));
    }

    private String buildSummary(int score, List<String> issues, int stepCount) {
        if (issues.isEmpty()) {
            return "计划质量良好，步骤数 " + stepCount + "，可以直接执行";
        }
        return "计划评分 " + score + "，存在 " + issues.size() + " 个风险点";
    }

    public record PlanQualityResult(
            Integer score,
            List<String> issues,
            String summary
    ) {
        public Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("qualityScore", score);
            metadata.put("qualityIssues", issues);
            metadata.put("qualitySummary", summary);
            return metadata;
        }
    }
}
