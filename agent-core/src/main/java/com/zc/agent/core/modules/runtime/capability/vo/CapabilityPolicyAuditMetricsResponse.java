package com.zc.agent.core.modules.runtime.capability.vo;

import java.util.List;

/**
 * Capability Policy 决策运营指标。
 *
 * @param totalCount 决策总数
 * @param allowCount 放行数
 * @param sandboxCount Sandbox 数
 * @param approvalCount 审批数
 * @param blockCount 阻断数
 * @param approvedReplayCount 审批续跑数
 * @param runCount Run 数
 * @param toolCount Tool 数
 * @param decisionBuckets 决策动作分布
 * @param capabilityBuckets Capability 分布
 * @param toolBuckets Tool 分布
 */
public record CapabilityPolicyAuditMetricsResponse(
        long totalCount,
        long allowCount,
        long sandboxCount,
        long approvalCount,
        long blockCount,
        long approvedReplayCount,
        long runCount,
        long toolCount,
        List<Bucket> decisionBuckets,
        List<Bucket> capabilityBuckets,
        List<Bucket> toolBuckets
) {
    /**
     * 运营聚合桶。
     *
     * @param key 聚合键
     * @param label 展示名称
     * @param count 决策数
     */
    public record Bucket(String key, String label, long count) {
    }
}
