package com.zc.agent.core.modules.conversation.dto;

/**
 * 审批工作台查询请求。
 */
public record ApprovalWorkbenchQueryRequest(
        Integer pageNum,
        Integer pageSize,
        String status,
        Long agentId,
        String conversationCode,
        String taskCode
) {
}
