package com.zc.agent.sdk.runtime;

/**
 * 审批提交参数。
 *
 * @param comment 审批说明
 * @param replanAfterReject 拒绝后是否重新规划
 */
public record RuntimeApprovalActionRequest(
        String comment,
        Boolean replanAfterReject
) {
    public static RuntimeApprovalActionRequest empty() {
        return new RuntimeApprovalActionRequest(null, null);
    }
}
