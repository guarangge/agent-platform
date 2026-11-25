package com.zc.agent.core.modules.runtime.capability.model;

/**
 * Capability Policy 决策指标数据库投影。
 *
 * @param totalCount 决策总数
 * @param allowCount 放行数
 * @param sandboxCount Sandbox 数
 * @param approvalCount 审批数
 * @param blockCount 阻断数
 * @param approvedReplayCount 审批授权后续跑决策数
 * @param runCount Run 数
 * @param toolCount Tool 数
 */
public record CapabilityPolicyAuditMetricsRecord(
        Long totalCount,
        Long allowCount,
        Long sandboxCount,
        Long approvalCount,
        Long blockCount,
        Long approvedReplayCount,
        Long runCount,
        Long toolCount
) {
}
