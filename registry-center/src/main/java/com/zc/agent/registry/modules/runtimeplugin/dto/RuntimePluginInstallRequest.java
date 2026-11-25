package com.zc.agent.registry.modules.runtimeplugin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Runtime Plugin 环境安装请求。
 *
 * @param versionId       安装版本 ID
 * @param environmentCode 环境编码
 * @param status          安装状态：enabled / disabled
 * @param metadata        非敏感安装元数据
 */
public record RuntimePluginInstallRequest(
        @NotNull(message = "插件版本 ID 不能为空") Long versionId,
        @NotBlank(message = "环境编码不能为空")
        @Size(max = 64, message = "环境编码长度不能超过64个字符")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "环境编码格式不合法")
        String environmentCode,
        @NotBlank(message = "安装状态不能为空")
        String status,
        JsonNode metadata
) {
}
