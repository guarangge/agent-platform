package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * Guardrail 策略命中响应。
 */
public record GuardrailPolicyHitResponse(
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
