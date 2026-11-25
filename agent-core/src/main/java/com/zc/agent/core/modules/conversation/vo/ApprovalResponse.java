package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 审批响应。
 *
 * @param approvalId      审批 ID
 * @param conversationId  会话 ID
 * @param taskId          任务 ID
 * @param taskStepId      任务步骤 ID
 * @param nodeId          Workflow 节点 ID
 * @param nodeName        Workflow 节点名称
 * @param status          审批状态
 * @param approvalComment 审批说明
 * @param approvedBy      审批人 ID
 * @param approvedAt      审批时间
 * @param metadata        扩展元数据
 * @param createdAt       创建时间
 */
public record ApprovalResponse(
        Long approvalId,
        Long conversationId,
        Long taskId,
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
