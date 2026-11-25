package com.zc.agent.core.modules.runtime.plugin.model;

import com.zc.agent.core.modules.runtime.capability.model.RuntimeCapability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runtime Plugin 的稳定声明模型。
 *
 * @param pluginCode         全局唯一插件编码
 * @param version            插件 SemVer 版本
 * @param platformApiVersion 目标平台 API 主版本
 * @param entrypoint         `AgentRuntimePlugin` 实现类全限定名
 * @param capabilities       插件声明使用的能力编码
 * @param dependencies       插件依赖及版本约束
 * @param components         按组件类型声明的稳定组件编码
 */
public record RuntimePluginManifest(
        String pluginCode,
        String version,
        String platformApiVersion,
        String entrypoint,
        List<String> capabilities,
        List<RuntimePluginDependency> dependencies,
        Map<String, List<String>> components
) {

    private static final Pattern PLUGIN_CODE_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:[-_.][a-z0-9]+)*$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final Pattern PLATFORM_API_VERSION_PATTERN = Pattern.compile("^[1-9]\\d*$");
    private static final Pattern ENTRYPOINT_PATTERN = Pattern.compile(
            "^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+$");
    private static final Pattern COMPONENT_CODE_PATTERN = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9._:-]*$");

    public RuntimePluginManifest {
        pluginCode = requireMatch(pluginCode, "pluginCode", PLUGIN_CODE_PATTERN);
        version = requireMatch(version, "version", SEMVER_PATTERN);
        platformApiVersion = requireMatch(
                platformApiVersion, "platformApiVersion", PLATFORM_API_VERSION_PATTERN);
        entrypoint = requireMatch(entrypoint, "entrypoint", ENTRYPOINT_PATTERN);
        capabilities = RuntimeCapability.normalize(capabilities);
        dependencies = immutableDependencies(dependencies);
        components = immutableComponents(components);
    }

    private static String requireMatch(String value, String field, Pattern pattern) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Runtime Plugin Manifest 字段不能为空: " + field);
        }
        String normalized = value.trim();
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Runtime Plugin Manifest 字段格式错误: " + field);
        }
        return normalized;
    }

    private static List<String> immutableUniqueValues(String field,
                                                      List<String> values,
                                                      Pattern pattern) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String item = requireMatch(value, field, pattern);
            if (!normalized.add(item)) {
                throw new IllegalArgumentException("Runtime Plugin Manifest 字段存在重复值: " + field + "." + item);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<RuntimePluginDependency> immutableDependencies(
            List<RuntimePluginDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        Set<String> dependencyCodes = new LinkedHashSet<>();
        for (RuntimePluginDependency dependency : dependencies) {
            if (dependency == null) {
                throw new IllegalArgumentException("Runtime Plugin Manifest dependencies 不能包含 null");
            }
            if (!dependencyCodes.add(dependency.pluginCode())) {
                throw new IllegalArgumentException(
                        "Runtime Plugin Manifest 存在重复依赖: " + dependency.pluginCode());
            }
        }
        return List.copyOf(dependencies);
    }

    private static Map<String, List<String>> immutableComponents(Map<String, List<String>> components) {
        if (components == null || components.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
        components.forEach((type, componentCodes) -> {
            RuntimePluginComponentType.require(type);
            normalized.put(type, immutableUniqueValues("components." + type, componentCodes,
                    COMPONENT_CODE_PATTERN));
        });
        return Collections.unmodifiableMap(normalized);
    }
}
