package com.zc.agent.registry.modules.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 覆盖更新 Agent Workflow 范围请求。
 *
 * @param workflowIds Workflow ID 列表，传空数组表示清空绑定
 */
public record UpdateAgentWorkflowsRequest(
        @NotNull(message = "Workflow ID 列表不能为空")
        @Size(max = 200, message = "Workflow 数量不能超过200个")
        List<Long> workflowIds
) {
}
