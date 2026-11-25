package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * Trace 查询请求。
 */
public record TaskTraceQueryRequest(
        @Min(1) Integer pageNum,
        @Min(1) @Max(200) Integer pageSize,
        String traceId,
        String eventType,
        String traceStatus,
        Long agentId,
        Long conversationId,
        Long taskId,
        String keyword,
        LocalDateTime startedFrom,
        LocalDateTime startedTo
) {
}
