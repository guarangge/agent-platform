package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Runtime Plugin 依赖声明请求。
 *
 * @param pluginCode        依赖插件编码
 * @param versionConstraint SemVer 版本约束
 * @param optional          是否允许依赖缺失
 */
public record RuntimePluginDependencyRequest(
        @NotBlank(message = "依赖插件编码不能为空")
        @Size(max = 64, message = "依赖插件编码长度不能超过64个字符")
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[-_.][a-z0-9]+)*$", message = "依赖插件编码格式不合法")
        String pluginCode,
        @NotBlank(message = "依赖版本约束不能为空")
        @Size(max = 128, message = "依赖版本约束长度不能超过128个字符")
        String versionConstraint,
        boolean optional
) {
}
