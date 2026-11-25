package com.zc.agent.registry.modules.agent.vo;

import java.util.List;

/**
 * Agent Workflow 范围响应。
 *
 * @param agentId               Agent ID
 * @param selectedWorkflowIds   已绑定 Workflow ID 列表
 * @param workflows             当前已绑定 Workflow 列表
 * @param availableWorkflows    可选 Workflow 列表
 */
public record AgentWorkflowsResponse(
        Long agentId,
        List<Long> selectedWorkflowIds,
        List<AgentWorkflowResponse> workflows,
        List<AgentWorkflowResponse> availableWorkflows
) {
}
