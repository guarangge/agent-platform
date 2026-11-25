package com.zc.agent.core.modules.conversation.dto;

/**
 * 审批动作请求。
 *
 * @param comment            审批说明
 * @param replanAfterReject  拒绝后是否生成二次规划
 */
public record ApprovalActionRequest(
        String comment,
        Boolean replanAfterReject
) {
}
