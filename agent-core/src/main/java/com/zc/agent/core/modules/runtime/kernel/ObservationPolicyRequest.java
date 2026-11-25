package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;

import java.util.List;

/**
 * ObservationPolicy 过渡期请求。
 *
 * @param executionContext 完整执行上下文
 * @param executionPlan 当前执行计划
 * @param currentStep 当前已执行步骤
 * @param previousStepResults 包含当前步骤的已完成结果
 * @param currentOutputSnapshot 当前步骤标准输出快照
 */
public record ObservationPolicyRequest(
        ExecutionContext executionContext,
        ExecutionPlan executionPlan,
        ExecutionPlan.ExecutionPlanStep currentStep,
        List<RuntimeStepResultContext> previousStepResults,
        String currentOutputSnapshot
) {
    public ObservationPolicyRequest {
        if (executionPlan == null) {
            throw new IllegalArgumentException("ObservationPolicy executionPlan 不能为空");
        }
        if (currentStep == null) {
            throw new IllegalArgumentException("ObservationPolicy currentStep 不能为空");
        }
        previousStepResults = previousStepResults == null ? List.of() : previousStepResults;
    }
}
