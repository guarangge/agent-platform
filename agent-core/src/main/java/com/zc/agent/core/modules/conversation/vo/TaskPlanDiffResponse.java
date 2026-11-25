package com.zc.agent.core.modules.conversation.vo;

import java.util.List;

/**
 * 计划差异响应。
 */
public record TaskPlanDiffResponse(
        Long taskId,
        Integer fromPlanVersion,
        Integer toPlanVersion,
        DiffSummaryResponse summary,
        List<PlanStepDiffResponse> changedSteps
) {
    public record DiffSummaryResponse(
            Integer addedCount,
            Integer removedCount,
            Integer changedCount
    ) {
    }

    public record PlanStepDiffResponse(
            String changeType,
            String stepKey,
            String beforeText,
            String afterText
    ) {
    }
}
