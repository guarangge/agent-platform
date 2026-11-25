package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Runtime Plugin 版本状态更新请求。
 *
 * @param status 版本状态：draft / published / deprecated
 */
public record RuntimePluginVersionStatusRequest(
        @NotBlank(message = "插件版本状态不能为空") String status
) {
}
