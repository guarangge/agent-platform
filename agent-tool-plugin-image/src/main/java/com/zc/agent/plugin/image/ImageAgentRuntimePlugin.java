package com.zc.agent.plugin.image;

import com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginComponentType;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;

import java.util.List;
import java.util.Map;

/** 图片理解 Runtime Plugin 入口。 */
public class ImageAgentRuntimePlugin implements AgentRuntimePlugin {

    private static final RuntimePluginManifest MANIFEST = new RuntimePluginManifest(
            "image-understanding-plugin",
            "0.0.1-SNAPSHOT",
            PLATFORM_API_VERSION,
            ImageAgentRuntimePlugin.class.getName(),
            List.of(),
            List.of(),
            Map.of(RuntimePluginComponentType.TOOLS.code(), List.of("IMAGE_OCR", "IMAGE_LAYOUT_ANALYZE"))
    );

    @Override
    public RuntimePluginManifest manifest() {
        return MANIFEST;
    }
}
