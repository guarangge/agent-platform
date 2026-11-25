package com.zc.agent.core.modules.tool.vo;

import java.time.LocalDateTime;

/**
 * Tool 最近相关 Trace 响应。
 */
public record ToolRelatedTraceResponse(
        Long conversationId,
        String conversationCode,
        Long taskId,
        String taskCode,
        String traceId,
        String eventType,
        String spanName,
        String traceStatus,
        String errorMessage,
        LocalDateTime startedAt
) {
}
