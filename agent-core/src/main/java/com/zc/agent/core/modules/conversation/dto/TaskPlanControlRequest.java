package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;

/** @param action pause/resume/skip_step/single_step @param targetStepNo 目标步骤 @param comment 操作说明 */
public record TaskPlanControlRequest(@NotBlank String action, Integer targetStepNo, String comment) {
}
