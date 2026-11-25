package com.zc.agent.registry.modules.policy.model;

import java.time.LocalDateTime;

/**
 * Guardrail 策略分页记录。
 */
public record GuardrailPolicyPageRecord(
        Long policyId,
        String policyCode,
        String policyName,
        Integer policyVersion,
        Integer priority,
        String actionType,
        String rolloutMode,
        String status,
        String description,
        Integer scopeUserCount,
        Integer scopeRoleCount,
        Integer scopeAgentCount,
        Integer scopeToolCount,
        Integer scopeWorkflowCount,
        Integer scopeEnvCount,
        LocalDateTime createdAt
) {
}
