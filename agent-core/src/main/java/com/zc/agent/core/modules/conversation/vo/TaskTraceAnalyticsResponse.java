package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * Trace 检索响应。
 */
public record TaskTraceAnalyticsResponse(
        Long taskTraceId,
        Long conversationId,
        String conversationCode,
        Long taskId,
        String taskCode,
        Long agentId,
        String agentName,
        String traceId,
        Long taskStepId,
        Integer stepNo,
        String stepName,
        String eventType,
        String spanName,
        String traceStatus,
        Long durationMs,
        String errorMessage,
        String inputSnapshot,
        String outputSnapshot,
        String metadata,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
