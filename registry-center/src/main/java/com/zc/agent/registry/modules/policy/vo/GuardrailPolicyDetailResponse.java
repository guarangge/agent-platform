package com.zc.agent.registry.modules.policy.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Guardrail 策略详情响应。
 */
public record GuardrailPolicyDetailResponse(
        Long policyId,
        String policyCode,
        String policyName,
        Integer policyVersion,
        Integer priority,
        String status,
        String description,
        String rolloutMode,
        Integer rolloutPercentage,
        List<Long> rolloutUserIds,
        List<Long> scopeUserIds,
        List<String> scopeRoleCodes,
        List<Long> scopeAgentIds,
        List<Long> scopeToolIds,
        List<Long> scopeWorkflowIds,
        List<String> scopeEnvs,
        String matchMode,
        Boolean requireNonEmptyInput,
        Integer maxInputLength,
        List<String> blockedKeywords,
        List<String> keywords,
        String actionType,
        String approvalNodeName,
        List<Long> approvalUserIds,
        List<String> approvalRoleCodes,
        String reason,
        JsonNode metadata,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
