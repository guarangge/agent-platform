package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 上下文压缩排查记录响应。
 */
public record CompactionTraceResponse(
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
        String stage,
        String tokenizerFamily,
        Integer qualityScore,
        String qualityStatus,
        Integer originalChars,
        Integer compactedChars,
        Integer savedChars,
        Integer estimatedOriginalTokens,
        Integer estimatedCompactedTokens,
        Integer savedTokens,
        Boolean summarized,
        Boolean truncated,
        String policyJson,
        String metadata,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
