package com.zc.agent.core.modules.conversation.model;

/**
 * Trace 聚合桶记录。
 */
public record TaskTraceMetricBucketRecord(
        String bucketKey,
        String bucketLabel,
        Long countValue
) {
}
