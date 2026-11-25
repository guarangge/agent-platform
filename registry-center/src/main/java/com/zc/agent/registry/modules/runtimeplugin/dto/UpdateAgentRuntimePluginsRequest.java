package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 覆盖更新 Agent Runtime Plugin 绑定请求。
 *
 * @param bindings 插件安装绑定列表，空数组表示清空
 */
public record UpdateAgentRuntimePluginsRequest(
        @Valid
        @NotNull(message = "插件绑定列表不能为空")
        @Size(max = 100, message = "Agent 插件绑定数量不能超过100个")
        List<@NotNull(message = "插件绑定项不能为空") AgentRuntimePluginBindingRequest> bindings
) {

    /**
     * 单个 Agent 插件安装绑定。
     *
     * @param installationId 插件环境安装 ID
     * @param required       插件不可用时是否阻断 Agent 运行
     */
    public record AgentRuntimePluginBindingRequest(
            @NotNull(message = "插件安装 ID 不能为空") Long installationId,
            boolean required
    ) {
    }
}
