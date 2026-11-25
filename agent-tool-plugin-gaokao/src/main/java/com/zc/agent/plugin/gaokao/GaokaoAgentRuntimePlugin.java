package com.zc.agent.plugin.gaokao;

import com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginComponentType;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;

import java.util.List;
import java.util.Map;

/** 高考志愿领域 Runtime Plugin 入口，仅声明组件，不承载执行逻辑。 */
public class GaokaoAgentRuntimePlugin implements AgentRuntimePlugin {

    private static final RuntimePluginManifest MANIFEST = new RuntimePluginManifest(
            "gaokao-tool-plugin",
            "0.0.1-SNAPSHOT",
            PLATFORM_API_VERSION,
            GaokaoAgentRuntimePlugin.class.getName(),
            List.of(),
            List.of(),
            Map.of(RuntimePluginComponentType.TOOLS.code(), List.of(
                    "SCORE_ANALYSIS",
                    "DOC_SEARCH",
                    "GAOKAO_POLICY_SEARCH",
                    "GAOKAO_UNIVERSITY_SEARCH",
                    "GAOKAO_MAJOR_SEARCH"
            ))
    );

    @Override
    public RuntimePluginManifest manifest() {
        return MANIFEST;
    }
}
