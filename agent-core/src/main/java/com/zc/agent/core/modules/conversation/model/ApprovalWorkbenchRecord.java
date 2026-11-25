package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 审批工作台记录。
 */
public record ApprovalWorkbenchRecord(
        Long approvalId,
        Long conversationId,
        String conversationCode,
        Long taskId,
        String taskCode,
        Long agentId,
        String agentName,
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
