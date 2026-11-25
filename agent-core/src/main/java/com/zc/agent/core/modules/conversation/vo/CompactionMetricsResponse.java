package com.zc.agent.core.modules.conversation.vo;

import java.util.List;

/**
 * 上下文压缩运营指标响应。
 */
public record CompactionMetricsResponse(
        Long totalCount,
        Long criticalCount,
        Long warningCount,
        Double avgQualityScore,
        Long avgSavedChars,
        Long avgSavedTokens,
        List<BucketItem> stageBuckets,
        List<BucketItem> qualityBuckets,
        List<CompactionTraceResponse> topSavedTokenRecords
) {

    public record BucketItem(
            String bucketKey,
            String bucketLabel,
            Long countValue
    ) {
    }
}
