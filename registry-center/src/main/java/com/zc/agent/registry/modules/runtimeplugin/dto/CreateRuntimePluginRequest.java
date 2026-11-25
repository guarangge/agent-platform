package com.zc.agent.registry.modules.runtimeplugin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建 Runtime Plugin 定义请求。
 *
 * @param pluginCode  项目内唯一插件编码
 * @param pluginName  插件名称
 * @param description 插件说明
 * @param status      定义状态：enabled / disabled
 * @param metadata    非敏感扩展元数据
 */
public record CreateRuntimePluginRequest(
        @NotBlank(message = "插件编码不能为空")
        @Size(max = 64, message = "插件编码长度不能超过64个字符")
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[-_.][a-z0-9]+)*$", message = "插件编码格式不合法")
        String pluginCode,
        @NotBlank(message = "插件名称不能为空")
        @Size(max = 128, message = "插件名称长度不能超过128个字符")
        String pluginName,
        @Size(max = 1000, message = "插件说明长度不能超过1000个字符")
        String description,
        @NotBlank(message = "插件状态不能为空")
        String status,
        JsonNode metadata
) {
}
