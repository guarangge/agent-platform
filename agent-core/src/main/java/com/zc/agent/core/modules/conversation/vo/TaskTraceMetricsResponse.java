package com.zc.agent.core.modules.conversation.vo;

import java.util.List;

/**
 * Trace 运营指标响应。
 */
public record TaskTraceMetricsResponse(
        Long totalCount,
        Long traceCount,
        Long failedCount,
        Double failureRate,
        Long avgDurationMs,
        Long approvalAvgWaitMs,
        Long approvalMaxWaitMs,
        Long compactionObservedCount,
        Long compactionCriticalCount,
        Double compactionAvgQualityScore,
        Long compactionAvgSavedTokens,
        List<TraceMetricBucketResponse> agentBuckets,
        List<TraceMetricBucketResponse> statusBuckets,
        List<TraceMetricBucketResponse> eventTypeBuckets,
        List<TaskTraceAnalyticsResponse> slowSpans
) {
    public record TraceMetricBucketResponse(
            String bucketKey,
            String bucketLabel,
            Long countValue
    ) {
    }
}
