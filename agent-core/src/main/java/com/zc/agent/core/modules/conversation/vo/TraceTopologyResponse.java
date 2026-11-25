package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trace 拓扑响应。
 */
public record TraceTopologyResponse(
        Long conversationId,
        Long taskId,
        String traceId,
        TraceTopologySummary summary,
        List<TraceSpanNode> spans,
        List<TraceApprovalNode> approvals,
        List<TraceDelegationNode> delegations
) {
    public record TraceTopologySummary(
            Integer spanCount,
            Integer successSpanCount,
            Integer failedSpanCount,
            Integer waitingApprovalSpanCount,
            Integer approvalCount,
            Integer pendingApprovalCount,
            Integer approvedApprovalCount,
            Integer rejectedApprovalCount,
            Integer timedOutApprovalCount,
            Integer revokedApprovalCount,
            Integer delegationCount,
            Integer successDelegationCount,
            Integer failedDelegationCount,
            Integer processingDelegationCount,
            Long totalDurationMs,
            Long wallClockDurationMs
    ) {
    }

    public record TraceSpanNode(
            Long taskTraceId,
            Long taskStepId,
            String eventType,
            String spanName,
            String traceStatus,
            Long durationMs,
            String errorMessage,
            String inputSnapshot,
            String outputSnapshot,
            String metadata,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }

    public record TraceApprovalNode(
            Long approvalId,
            Long taskStepId,
            String nodeId,
            String nodeName,
            String status,
            String approvalComment,
            Long approvedBy,
            LocalDateTime approvedAt,
            String metadata,
            LocalDateTime createdAt
    ) {
    }

    public record TraceDelegationNode(
            Long delegationId,
            Long parentTaskId,
            Long parentTaskStepId,
            Long childTaskId,
            Long delegateAgentId,
            String delegateAgentCode,
            String delegateAgentName,
            String status,
            String traceId,
            String inputSnapshot,
            String outputSnapshot,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
