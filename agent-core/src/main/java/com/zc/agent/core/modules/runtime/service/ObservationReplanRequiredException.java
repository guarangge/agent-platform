package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;

import java.util.List;

/**
 * 步骤成功后基于 observation 触发重规划。
 */
public class ObservationReplanRequiredException extends BusinessException {

    private final ExecutionPlan.ExecutionPlanStep triggerStep;
    private final Long triggerTaskStepId;
    private final List<RuntimeStepResultContext> previousStepResults;

    public ObservationReplanRequiredException(ExecutionPlan.ExecutionPlanStep triggerStep,
                                              Long triggerTaskStepId,
                                              List<RuntimeStepResultContext> previousStepResults,
                                              String message) {
        super(message);
        this.triggerStep = triggerStep;
        this.triggerTaskStepId = triggerTaskStepId;
        this.previousStepResults = previousStepResults;
    }

    public ExecutionPlan.ExecutionPlanStep triggerStep() {
        return triggerStep;
    }

    public Long triggerTaskStepId() {
        return triggerTaskStepId;
    }

    public List<RuntimeStepResultContext> previousStepResults() {
        return previousStepResults;
    }
}
