package com.zc.agent.registry.modules.policy.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 运行时 Guardrail 策略响应。
 */
public record RuntimeGuardrailPolicyResponse(
        String policyCode,
        String policyName,
        Integer version,
        Boolean enabled,
        Integer priority,
        String layer,
        Map<String, Object> scope,
        Map<String, Object> rollout,
        Map<String, Object> match,
        Map<String, Object> action,
        JsonNode metadata
) {
}
