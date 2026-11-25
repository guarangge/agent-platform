package com.zc.agent.registry.modules.policy.model;

import java.time.LocalDateTime;

/**
 * Guardrail 策略详情记录。
 */
public record GuardrailPolicyDetailRecord(
        Long policyId,
        String policyCode,
        String policyName,
        Integer policyVersion,
        Integer priority,
        String status,
        String description,
        String rolloutMode,
        Integer rolloutPercentage,
        String rolloutUserIdsJson,
        String scopeUserIdsJson,
        String scopeRoleCodesJson,
        String scopeAgentIdsJson,
        String scopeToolIdsJson,
        String scopeWorkflowIdsJson,
        String scopeEnvsJson,
        String matchMode,
        Boolean requireNonEmptyInput,
        Integer maxInputLength,
        String blockedKeywordsJson,
        String keywordsJson,
        String actionType,
        String approvalNodeName,
        String approvalUserIdsJson,
        String approvalRoleCodesJson,
        String reason,
        String metadataJson,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
