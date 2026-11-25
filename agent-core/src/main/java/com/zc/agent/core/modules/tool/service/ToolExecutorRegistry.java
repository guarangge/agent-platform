package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin;
import com.zc.agent.core.modules.runtime.plugin.RuntimePluginDiscovery;
import com.zc.agent.core.modules.runtime.plugin.RuntimePluginValidationException;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginComponentType;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 内置及扩展 ToolExecutor 注册表，统一处理发现、冲突检测和确定性解析。
 */
@Component
public class ToolExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorRegistry.class);

    private final List<ToolExecutor> executors;
    private final Map<String, List<String>> pluginOwnersByToolCode;

    @Autowired
    public ToolExecutorRegistry(List<ToolExecutor> executors,
                                RuntimePluginDiscovery pluginDiscovery) {
        this(executors, pluginDiscovery.plugins());
    }

    /** 测试和未启用 Runtime Plugin 发现时保留原 ToolExecutor 注册方式。 */
    public ToolExecutorRegistry(List<ToolExecutor> executors) {
        this(executors, List.of());
    }

    ToolExecutorRegistry(List<ToolExecutor> executors,
                         List<AgentRuntimePlugin> plugins) {
        this.executors = executors.stream()
                .sorted(Comparator.comparing(item -> item.getClass().getName()))
                .toList();
        this.pluginOwnersByToolCode = buildPluginToolOwners(plugins);
        validatePluginToolExecutors();
        log.info("已加载 ToolExecutor 扩展, count={}, executors={}, pluginTools={}",
                this.executors.size(),
                this.executors.stream().map(item -> item.getClass().getName()).toList(),
                pluginOwnersByToolCode);
    }

    /**
     * 按 Tool code 解析唯一执行器；冲突时直接阻断，避免依赖 Bean 顺序随机路由。
     */
    public ToolExecutor resolve(String toolCode) {
        List<ToolExecutor> matched = executors.stream()
                .filter(executor -> executor.supports(toolCode))
                .toList();
        if (matched.isEmpty()) {
            throw new ToolExecutionException(
                    "未找到 Tool 执行器: " + toolCode,
                    "TOOL_EXECUTOR_NOT_FOUND",
                    "configuration",
                    false,
                    null,
                    Map.of("toolCode", toolCode == null ? "" : toolCode)
            );
        }
        if (matched.size() > 1) {
            throw new ToolExecutionException(
                    "Tool 执行器注册冲突: " + toolCode,
                    "TOOL_EXECUTOR_CONFLICT",
                    "configuration",
                    false,
                    null,
                    Map.of(
                            "toolCode", toolCode == null ? "" : toolCode,
                            "executors", matched.stream().map(item -> item.getClass().getName()).toList()
                    )
            );
        }
        return matched.get(0);
    }

    public List<String> registeredExecutors() {
        return executors.stream().map(item -> item.getClass().getName()).toList();
    }

    /** 返回 Manifest 声明的 Tool code 与插件归属，供健康检查和后续治理使用。 */
    public Map<String, List<String>> registeredPluginTools() {
        return pluginOwnersByToolCode;
    }

    /** 返回支持指定 Tool code 的执行器实现类，供插件健康观测使用。 */
    public List<String> supportingExecutorClasses(String toolCode) {
        return executors.stream()
                .filter(executor -> executor.supports(toolCode))
                .map(executor -> executor.getClass().getName())
                .toList();
    }

    private Map<String, List<String>> buildPluginToolOwners(List<AgentRuntimePlugin> plugins) {
        TreeMap<String, List<String>> owners = new TreeMap<>();
        for (AgentRuntimePlugin plugin : plugins) {
            List<String> toolCodes = plugin.manifest().components()
                    .getOrDefault(RuntimePluginComponentType.TOOLS.code(), List.of());
            for (String toolCode : toolCodes) {
                owners.computeIfAbsent(toolCode, ignored -> new ArrayList<>())
                        .add(plugin.manifest().pluginCode());
            }
        }
        LinkedHashMap<String, List<String>> immutableOwners = new LinkedHashMap<>();
        owners.forEach((toolCode, pluginCodes) -> immutableOwners.put(
                toolCode, pluginCodes.stream().sorted().toList()));
        return Collections.unmodifiableMap(immutableOwners);
    }

    private void validatePluginToolExecutors() {
        pluginOwnersByToolCode.forEach((toolCode, pluginCodes) -> {
            List<String> matchedExecutors = executors.stream()
                    .filter(executor -> executor.supports(toolCode))
                    .map(executor -> executor.getClass().getName())
                    .toList();
            if (matchedExecutors.isEmpty()) {
                throw new RuntimePluginValidationException(
                        "Runtime Plugin Tool 缺少执行器: " + toolCode,
                        "PLUGIN_TOOL_EXECUTOR_MISSING",
                        Map.of("toolCode", toolCode, "pluginCodes", pluginCodes));
            }
            if (matchedExecutors.size() > 1) {
                throw new RuntimePluginValidationException(
                        "Runtime Plugin Tool 执行器冲突: " + toolCode,
                        "PLUGIN_TOOL_EXECUTOR_CONFLICT",
                        Map.of(
                                "toolCode", toolCode,
                                "pluginCodes", pluginCodes,
                                "executors", matchedExecutors
                        ));
            }
        });
    }
}
