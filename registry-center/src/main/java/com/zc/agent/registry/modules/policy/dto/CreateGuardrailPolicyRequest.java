package com.zc.agent.registry.modules.policy.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 新增 Guardrail 策略请求。
 */
public record CreateGuardrailPolicyRequest(
        @NotBlank(message = "策略编码不能为空")
        String policyCode,
        @NotBlank(message = "策略名称不能为空")
        String policyName,
        @NotNull(message = "策略版本不能为空")
        @Min(value = 1, message = "策略版本最小为 1")
        Integer policyVersion,
        @NotNull(message = "优先级不能为空")
        Integer priority,
        @NotBlank(message = "状态不能为空")
        String status,
        String description,
        @NotBlank(message = "灰度模式不能为空")
        String rolloutMode,
        @Min(value = 0, message = "灰度百分比不能小于 0")
        @Max(value = 100, message = "灰度百分比不能大于 100")
        Integer rolloutPercentage,
        List<Long> rolloutUserIds,
        List<Long> scopeUserIds,
        List<String> scopeRoleCodes,
        List<Long> scopeAgentIds,
        List<Long> scopeToolIds,
        List<Long> scopeWorkflowIds,
        List<String> scopeEnvs,
        @NotBlank(message = "匹配模式不能为空")
        String matchMode,
        Boolean requireNonEmptyInput,
        @Min(value = 0, message = "最大输入长度不能小于 0")
        Integer maxInputLength,
        List<String> blockedKeywords,
        List<String> keywords,
        @NotBlank(message = "动作类型不能为空")
        String actionType,
        String approvalNodeName,
        List<Long> approvalUserIds,
        List<String> approvalRoleCodes,
        String reason,
        JsonNode metadata,
        String remark
) {
}
