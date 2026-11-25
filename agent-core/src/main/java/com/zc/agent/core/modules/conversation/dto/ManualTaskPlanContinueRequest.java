package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 人工改计划继续执行请求。
 */
public record ManualTaskPlanContinueRequest(
        @NotBlank String goal,
        @NotBlank String planType,
        List<String> assumptions,
        List<String> constraints,
        List<String> risks,
        String comment,
        @Valid @NotEmpty List<ManualTaskPlanStepRequest> steps
) {
    public record ManualTaskPlanStepRequest(
            @NotNull Integer stepNo,
            @NotBlank String stepId,
            @NotBlank String stepType,
            @NotBlank String stepName,
            String objective,
            List<String> dependsOnStepIds,
            String targetToolCode,
            Long targetWorkflowId,
            Map<String, Object> inputs,
            String expectedOutput,
            String failurePolicy,
            Boolean replannable,
            String status,
            String description
    ) {
    }
}
