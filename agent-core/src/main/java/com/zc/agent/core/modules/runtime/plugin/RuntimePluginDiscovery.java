package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * 通过 Java ServiceLoader 发现随应用启动加载的 Runtime Plugin。
 * 第一阶段不创建独立 ClassLoader，插件由应用当前上下文 ClassLoader 提供。
 */
@Component
public class RuntimePluginDiscovery {

    private static final Logger log = LoggerFactory.getLogger(RuntimePluginDiscovery.class);

    private final List<AgentRuntimePlugin> plugins;

    @Autowired
    public RuntimePluginDiscovery(RuntimePluginValidator validator) {
        this(resolveContextClassLoader(), validator);
    }

    RuntimePluginDiscovery(ClassLoader classLoader) {
        this(classLoader, new RuntimePluginValidator());
    }

    RuntimePluginDiscovery(ClassLoader classLoader,
                           RuntimePluginValidator validator) {
        List<AgentRuntimePlugin> discoveredPlugins = StreamSupport.stream(
                        ServiceLoader.load(AgentRuntimePlugin.class, classLoader).spliterator(), false)
                .peek(this::requireManifest)
                .sorted(Comparator
                        .comparing((AgentRuntimePlugin plugin) -> plugin.manifest().pluginCode())
                        .thenComparing(plugin -> plugin.manifest().version())
                        .thenComparing(plugin -> plugin.getClass().getName()))
                .toList();
        validator.validate(discoveredPlugins);
        this.plugins = discoveredPlugins;
        log.info("已发现 Runtime Plugin, count={}, plugins={}",
                plugins.size(),
                plugins.stream().map(plugin -> plugin.manifest().pluginCode()
                        + ":" + plugin.manifest().version()).toList());
    }

    /** 返回按插件编码、版本和入口类稳定排序的插件列表。 */
    public List<AgentRuntimePlugin> plugins() {
        return plugins;
    }

    private AgentRuntimePlugin requireManifest(AgentRuntimePlugin plugin) {
        RuntimePluginManifest manifest = plugin.manifest();
        if (manifest == null) {
            throw new RuntimePluginValidationException(
                    "Runtime Plugin 未返回 Manifest: " + plugin.getClass().getName(),
                    "PLUGIN_MANIFEST_MISSING",
                    java.util.Map.of("providerClass", plugin.getClass().getName()));
        }
        return plugin;
    }

    private static ClassLoader resolveContextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? AgentRuntimePlugin.class.getClassLoader() : classLoader;
    }
}
