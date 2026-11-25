package com.zc.agent.registry.modules.role.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 覆盖更新角色 Agent 请求。
 *
 * @param agentIds Agent ID 列表，传空数组表示清空角色 Agent 绑定
 */
public record UpdateRoleAgentsRequest(
        @NotNull(message = "Agent ID 列表不能为空")
        @Size(max = 200, message = "Agent 数量不能超过200个")
        List<Long> agentIds
) {
}
