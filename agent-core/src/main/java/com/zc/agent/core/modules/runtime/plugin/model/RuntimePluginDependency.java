package com.zc.agent.core.modules.runtime.plugin.model;

import java.util.regex.Pattern;

/**
 * Runtime Plugin 依赖声明。
 *
 * @param pluginCode        依赖插件编码
 * @param versionConstraint 版本约束表达式
 * @param optional          是否为可选依赖
 */
public record RuntimePluginDependency(
        String pluginCode,
        String versionConstraint,
        boolean optional
) {

    private static final Pattern PLUGIN_CODE_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:[-_.][a-z0-9]+)*$");

    public RuntimePluginDependency {
        pluginCode = requireText(pluginCode, "dependency.pluginCode");
        if (!PLUGIN_CODE_PATTERN.matcher(pluginCode).matches()) {
            throw new IllegalArgumentException(
                    "Runtime Plugin Manifest 字段格式错误: dependency.pluginCode");
        }
        versionConstraint = requireText(versionConstraint, "dependency.versionConstraint");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Runtime Plugin Manifest 字段不能为空: " + field);
        }
        return value.trim();
    }
}
