package com.zc.agent.core.modules.conversation.model;

/**
 * Trace 总览指标记录。
 */
public record TaskTraceMetricsRecord(
        Long totalCount,
        Long traceCount,
        Long failedCount,
        Long avgDurationMs
) {
}
