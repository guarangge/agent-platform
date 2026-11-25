package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 审批或恢复操作返回的稳定 Task 字段。
 *
 * @param taskId Task ID
 * @param conversationId Thread ID
 * @param status Task 状态
 * @param errorMessage 错误信息
 * @param approvals 审批记录
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeTask(
        Long taskId,
        Long conversationId,
        String status,
        String errorMessage,
        List<RuntimeApproval> approvals
) {
}
