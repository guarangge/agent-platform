package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginDependency;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 在 Runtime Plugin 注册前统一校验兼容性、唯一性和依赖图。 */
@Component
public class RuntimePluginValidator {

    private final RuntimePluginVersionMatcher versionMatcher;

    public RuntimePluginValidator() {
        this.versionMatcher = new RuntimePluginVersionMatcher();
    }

    /**
     * 校验本次启动发现的完整插件集合，失败时阻断应用启动。
     *
     * @param plugins 已发现插件
     */
    public void validate(List<AgentRuntimePlugin> plugins) {
        Map<String, RuntimePluginManifest> manifests = indexAndValidateIdentity(plugins);
        validateComponentConflicts(manifests);
        validateDependencies(manifests);
        validateDependencyCycles(manifests);
    }

    private Map<String, RuntimePluginManifest> indexAndValidateIdentity(List<AgentRuntimePlugin> plugins) {
        LinkedHashMap<String, RuntimePluginManifest> manifests = new LinkedHashMap<>();
        for (AgentRuntimePlugin plugin : plugins) {
            RuntimePluginManifest manifest = plugin.manifest();
            if (!AgentRuntimePlugin.PLATFORM_API_VERSION.equals(manifest.platformApiVersion())) {
                throw failure(
                        "PLUGIN_API_VERSION_INCOMPATIBLE",
                        "Runtime Plugin 平台 API 版本不兼容: " + manifest.pluginCode(),
                        Map.of(
                                "pluginCode", manifest.pluginCode(),
                                "pluginVersion", manifest.version(),
                                "requiredPlatformApiVersion", manifest.platformApiVersion(),
                                "supportedPlatformApiVersion", AgentRuntimePlugin.PLATFORM_API_VERSION
                        ));
            }
            if (!plugin.getClass().getName().equals(manifest.entrypoint())) {
                throw failure(
                        "PLUGIN_ENTRYPOINT_MISMATCH",
                        "Runtime Plugin 入口类与 Manifest 不一致: " + manifest.pluginCode(),
                        Map.of(
                                "pluginCode", manifest.pluginCode(),
                                "manifestEntrypoint", manifest.entrypoint(),
                                "providerClass", plugin.getClass().getName()
                        ));
            }
            RuntimePluginManifest existing = manifests.putIfAbsent(manifest.pluginCode(), manifest);
            if (existing != null) {
                throw failure(
                        "PLUGIN_CODE_CONFLICT",
                        "Runtime Plugin 编码重复: " + manifest.pluginCode(),
                        Map.of(
                                "pluginCode", manifest.pluginCode(),
                                "versions", List.of(existing.version(), manifest.version())
                        ));
            }
        }
        return manifests;
    }

    private void validateComponentConflicts(Map<String, RuntimePluginManifest> manifests) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (RuntimePluginManifest manifest : manifests.values()) {
            manifest.components().forEach((componentType, componentCodes) -> {
                for (String componentCode : componentCodes) {
                    String componentKey = componentType + ":" + componentCode;
                    String existingOwner = owners.putIfAbsent(componentKey, manifest.pluginCode());
                    if (existingOwner != null) {
                        throw failure(
                                "PLUGIN_COMPONENT_CONFLICT",
                                "Runtime Plugin 组件重复声明: " + componentKey,
                                Map.of(
                                        "componentType", componentType,
                                        "componentCode", componentCode,
                                        "pluginCodes", List.of(existingOwner, manifest.pluginCode())
                                ));
                    }
                }
            });
        }
    }

    private void validateDependencies(Map<String, RuntimePluginManifest> manifests) {
        for (RuntimePluginManifest manifest : manifests.values()) {
            for (RuntimePluginDependency dependency : manifest.dependencies()) {
                RuntimePluginManifest installed = manifests.get(dependency.pluginCode());
                if (installed == null) {
                    if (!dependency.optional()) {
                        throw failure(
                                "PLUGIN_DEPENDENCY_MISSING",
                                "Runtime Plugin 缺少必需依赖: " + dependency.pluginCode(),
                                Map.of(
                                        "pluginCode", manifest.pluginCode(),
                                        "dependencyCode", dependency.pluginCode(),
                                        "versionConstraint", dependency.versionConstraint()
                                ));
                    }
                    continue;
                }
                boolean matched;
                try {
                    matched = versionMatcher.matches(installed.version(), dependency.versionConstraint());
                } catch (IllegalArgumentException exception) {
                    throw failure(
                            "PLUGIN_VERSION_CONSTRAINT_INVALID",
                            "Runtime Plugin 依赖版本约束无效: " + dependency.versionConstraint(),
                            Map.of(
                                    "pluginCode", manifest.pluginCode(),
                                    "dependencyCode", dependency.pluginCode(),
                                    "versionConstraint", dependency.versionConstraint()
                            ));
                }
                if (!matched) {
                    throw failure(
                            "PLUGIN_DEPENDENCY_VERSION_MISMATCH",
                            "Runtime Plugin 依赖版本不匹配: " + dependency.pluginCode(),
                            Map.of(
                                    "pluginCode", manifest.pluginCode(),
                                    "dependencyCode", dependency.pluginCode(),
                                    "installedVersion", installed.version(),
                                    "versionConstraint", dependency.versionConstraint()
                            ));
                }
            }
        }
    }

    private void validateDependencyCycles(Map<String, RuntimePluginManifest> manifests) {
        Map<String, Integer> states = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String pluginCode : manifests.keySet()) {
            visit(pluginCode, manifests, states, path);
        }
    }

    private void visit(String pluginCode,
                       Map<String, RuntimePluginManifest> manifests,
                       Map<String, Integer> states,
                       Deque<String> path) {
        if (states.getOrDefault(pluginCode, 0) == 2) {
            return;
        }
        states.put(pluginCode, 1);
        path.addLast(pluginCode);
        for (RuntimePluginDependency dependency : manifests.get(pluginCode).dependencies()) {
            if (!manifests.containsKey(dependency.pluginCode())) {
                continue;
            }
            int dependencyState = states.getOrDefault(dependency.pluginCode(), 0);
            if (dependencyState == 1) {
                List<String> cycle = new ArrayList<>();
                boolean collecting = false;
                for (String item : path) {
                    if (item.equals(dependency.pluginCode())) {
                        collecting = true;
                    }
                    if (collecting) {
                        cycle.add(item);
                    }
                }
                cycle.add(dependency.pluginCode());
                throw failure(
                        "PLUGIN_DEPENDENCY_CYCLE",
                        "Runtime Plugin 依赖存在循环: " + String.join(" -> ", cycle),
                        Map.of("cycle", List.copyOf(cycle)));
            }
            if (dependencyState == 0) {
                visit(dependency.pluginCode(), manifests, states, path);
            }
        }
        path.removeLast();
        states.put(pluginCode, 2);
    }

    private RuntimePluginValidationException failure(String errorCode,
                                                     String message,
                                                     Map<String, Object> details) {
        return new RuntimePluginValidationException(message, errorCode, details);
    }
}
