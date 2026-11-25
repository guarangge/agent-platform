package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;

import java.util.List;

/**
 * 计划步骤执行失败异常，携带失败步骤与前序结果，供自动重规划使用。
 */
public class PlanStepExecutionException extends BusinessException {

    private final ExecutionPlan.ExecutionPlanStep failedStep;

    private final Long failedTaskStepId;

    private final List<RuntimeStepResultContext> previousStepResults;
    private final ExecutionFailureClassifierService.FailureSemantics failureSemantics;

    public PlanStepExecutionException(ExecutionPlan.ExecutionPlanStep failedStep,
                                      Long failedTaskStepId,
                                      List<RuntimeStepResultContext> previousStepResults,
                                      String message,
                                      Throwable cause,
                                      ExecutionFailureClassifierService.FailureSemantics failureSemantics) {
        super(message);
        initCause(cause);
        this.failedStep = failedStep;
        this.failedTaskStepId = failedTaskStepId;
        this.previousStepResults = previousStepResults;
        this.failureSemantics = failureSemantics;
    }

    public ExecutionPlan.ExecutionPlanStep failedStep() {
        return failedStep;
    }

    public Long failedTaskStepId() {
        return failedTaskStepId;
    }

    public List<RuntimeStepResultContext> previousStepResults() {
        return previousStepResults;
    }

    public ExecutionFailureClassifierService.FailureSemantics failureSemantics() {
        return failureSemantics;
    }
}
