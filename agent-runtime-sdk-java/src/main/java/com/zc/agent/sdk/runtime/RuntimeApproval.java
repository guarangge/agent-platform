package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Runtime Task 的审批摘要。
 *
 * @param approvalId 审批 ID
 * @param taskId Task ID
 * @param status 审批状态
 * @param nodeId 节点 ID
 * @param nodeName 节点名称
 * @param approvalComment 审批说明
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeApproval(
        Long approvalId,
        Long taskId,
        String status,
        String nodeId,
        String nodeName,
        String approvalComment
) {
}
