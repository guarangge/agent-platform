package com.zc.agent.core.modules.conversation.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Guardrail 策略命中分页查询请求。
 */
public record GuardrailPolicyHitPageQueryRequest(
        Integer pageNum,
        Integer pageSize,
        String policyCode,
        String decisionType,
        String traceId,
        Long taskId,
        Long agentId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startedTo
) {
}
