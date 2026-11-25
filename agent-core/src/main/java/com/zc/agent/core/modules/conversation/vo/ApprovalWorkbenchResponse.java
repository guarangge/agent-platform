package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批工作台响应。
 */
public record ApprovalWorkbenchResponse(
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
        List<Long> approvalUserIds,
        List<String> approvalRoleCodes,
        Integer timeoutMinutes,
        LocalDateTime deadlineAt,
        Long waitingMinutes,
        String approvalSourceType,
        Integer nextPlanStepNo,
        String metadata,
        LocalDateTime createdAt
) {
}
