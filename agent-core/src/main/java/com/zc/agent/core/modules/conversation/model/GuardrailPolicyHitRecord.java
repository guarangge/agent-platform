package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * Guardrail 策略命中记录。
 */
public record GuardrailPolicyHitRecord(
        Long hitId,
        Long conversationId,
        Long taskId,
        Long taskStepId,
        Long agentId,
        String traceId,
        String policyCode,
        String policyName,
        Integer policyVersion,
        String decisionType,
        String resourceKind,
        Long resourceId,
        String resourceCode,
        String resourceName,
        String hitDetail,
        String metadata,
        LocalDateTime createdAt
) {
}
