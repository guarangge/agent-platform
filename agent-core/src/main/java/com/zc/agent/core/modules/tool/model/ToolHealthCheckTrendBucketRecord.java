package com.zc.agent.core.modules.tool.model;

/**
 * Tool 健康检查趋势桶记录。
 */
public record ToolHealthCheckTrendBucketRecord(
        String bucketKey,
        String bucketLabel,
        Long totalCount,
        Long successCount,
        Long failedCount
) {
}
