package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;

/**
 * Agent 运行时插件统一 SPI。
 * 插件只声明身份、兼容版本、能力和组件，具体组件仍由对应运行时扩展点执行。
 */
public interface AgentRuntimePlugin {

    /** 当前平台支持的 Runtime Plugin API 主版本。 */
    String PLATFORM_API_VERSION = "1";

    /** 插件 Jar 内 Manifest 的固定资源路径。 */
    String MANIFEST_RESOURCE = "META-INF/agent-runtime-plugin.json";

    /**
     * 返回插件的不可变声明。
     *
     * @return Runtime Plugin Manifest
     */
    RuntimePluginManifest manifest();
}
