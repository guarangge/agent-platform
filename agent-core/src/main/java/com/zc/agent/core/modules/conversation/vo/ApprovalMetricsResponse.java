package com.zc.agent.core.modules.conversation.vo;

/**
 * 审批 SLA 指标响应。
 */
public record ApprovalMetricsResponse(
        Long conversationId,
        Integer totalCount,
        Integer pendingCount,
        Integer approvedCount,
        Integer rejectedCount,
        Integer timedOutCount,
        Integer revokedCount,
        Integer overduePendingCount,
        Long averageWaitMs,
        Long maxWaitMs
) {
}
