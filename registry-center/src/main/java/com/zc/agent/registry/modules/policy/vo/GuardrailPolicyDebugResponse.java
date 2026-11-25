package com.zc.agent.registry.modules.policy.vo;

import java.util.List;
import java.util.Map;

/**
 * Guardrail 策略调试响应。
 */
public record GuardrailPolicyDebugResponse(
        String finalDecision,
        Map<String, Object> primaryPolicy,
        List<Map<String, Object>> matchedPolicies,
        List<Map<String, Object>> allEffectivePolicies
) {
}
