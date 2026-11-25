package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Runtime Plugin 安装状态更新请求。
 *
 * @param status 安装状态：enabled / disabled / uninstalled
 */
public record RuntimePluginInstallationStatusRequest(
        @NotBlank(message = "安装状态不能为空") String status
) {
}
