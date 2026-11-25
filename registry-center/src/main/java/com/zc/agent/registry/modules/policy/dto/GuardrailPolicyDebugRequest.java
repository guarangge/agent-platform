package com.zc.agent.registry.modules.policy.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Guardrail 策略调试请求。
 */
public record GuardrailPolicyDebugRequest(
        Long userId,
        List<String> roleCodes,
        Long agentId,
        Long toolId,
        Long workflowId,
        @NotBlank(message = "环境不能为空")
        String environment,
        String inputText
) {
}
