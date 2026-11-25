package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话委派任务树响应。
 */
public record TaskDelegationTreeResponse(
        Long conversationId,
        DelegationTreeSummaryResponse summary,
        List<DelegationTreeNodeResponse> roots,
        List<DelegationAuditResponse> audits
) {
    public record DelegationTreeSummaryResponse(
            Integer totalTaskCount,
            Integer delegatedTaskCount,
            Integer successTaskCount,
            Integer failedTaskCount,
            Integer waitingApprovalTaskCount,
            Integer maxDepth
    ) {
    }

    public record DelegationTreeNodeResponse(
            Long taskId,
            String taskCode,
            Long agentId,
            String agentName,
            String taskKind,
            String executionType,
            String traceId,
            String status,
            Long parentTaskId,
            Long rootTaskId,
            Long triggerTaskStepId,
            Integer currentPlanVersion,
            String resultSummary,
            String errorMessage,
            Integer stepCount,
            Integer traceCount,
            Integer approvalCount,
            Integer childCount,
            Integer descendantCount,
            Integer successDescendantCount,
            Integer failedDescendantCount,
            Integer waitingApprovalDescendantCount,
            Integer depth,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<DelegationTreeNodeResponse> children
    ) {
    }

    public record DelegationAuditResponse(
            Long delegationId,
            Long parentTaskId,
            String parentTaskCode,
            Long parentTaskStepId,
            Long childTaskId,
            String childTaskCode,
            String childTaskStatus,
            Long delegateAgentId,
            String delegateAgentCode,
            String delegateAgentName,
            String delegateInstruction,
            String traceId,
            String status,
            String childResultSummary,
            String outputSnapshot,
            String errorMessage,
            String auditChain,
            String createdAt,
            String updatedAt
    ) {
    }
}
