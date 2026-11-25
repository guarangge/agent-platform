package com.zc.agent.registry.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 覆盖更新 Agent Tool 范围请求。
 *
 * @param toolScopeMode Tool 范围模式，支持 custom / inherit_default / inherit_and_append
 * @param toolIds       Tool ID 列表，传空数组表示清空自定义 Tool 绑定
 */
public record UpdateAgentToolsRequest(
        @NotBlank(message = "Tool 范围模式不能为空")
        @Size(max = 32, message = "Tool 范围模式长度不能超过32个字符")
        String toolScopeMode,
        @NotNull(message = "Tool ID 列表不能为空")
        @Size(max = 200, message = "Tool 数量不能超过200个")
        List<Long> toolIds
) {
}
