package com.zc.agent.core.modules.tool.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tool 健康检查聚合指标响应。
 */
public record ToolHealthCheckMetricsResponse(
        Long totalCount,
        Long successCount,
        Long failedCount,
        Double successRate,
        Long avgLatencyMs,
        LocalDateTime lastCheckedAt,
        LocalDateTime lastSuccessAt,
        String latestFailureReason,
        Integer consecutiveFailureCount,
        Integer consecutiveFailureAlertThreshold,
        Boolean consecutiveFailureAlertTriggered,
        Double slaTargetSuccessRate,
        Boolean slaBreached,
        ToolHealthCheckHistoryResponse latestRecord,
        List<ToolHealthCheckTrendBucketResponse> trendBuckets,
        List<ToolRelatedTraceResponse> relatedTraces
) {
    public record ToolHealthCheckTrendBucketResponse(
            String bucketKey,
            String bucketLabel,
            Long totalCount,
            Long successCount,
            Long failedCount
    ) {
    }
}
