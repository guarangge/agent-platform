package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Runtime Plugin 定义状态更新请求。
 *
 * @param status 定义状态：enabled / disabled
 */
public record RuntimePluginStatusRequest(
        @NotBlank(message = "插件状态不能为空") String status
) {
}
