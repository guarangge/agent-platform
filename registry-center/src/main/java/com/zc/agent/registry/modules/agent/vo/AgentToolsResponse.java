package com.zc.agent.registry.modules.agent.vo;

import java.util.List;

/**
 * Agent Tool 范围响应。
 *
 * @param agentId        Agent ID
 * @param toolScopeMode  Tool 范围模式
 * @param selectedToolIds 已绑定 Tool ID 列表
 * @param tools          当前已绑定 Tool 列表
 * @param availableTools 可选 Tool 列表
 */
public record AgentToolsResponse(
        Long agentId,
        String toolScopeMode,
        List<Long> selectedToolIds,
        List<AgentToolResponse> tools,
        List<AgentToolResponse> availableTools
) {
}
