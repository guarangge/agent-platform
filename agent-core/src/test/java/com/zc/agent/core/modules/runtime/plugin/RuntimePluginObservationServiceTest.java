package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginDependency;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import com.zc.agent.core.modules.tool.service.ToolExecutorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimePluginObservationServiceTest {

    @Test
    void shouldObserveLoadedPluginDependenciesAndToolComponents() {
        RuntimePluginDiscovery discovery = mock(RuntimePluginDiscovery.class);
        ToolExecutorRegistry toolRegistry = mock(ToolExecutorRegistry.class);
        AgentRuntimePlugin base = plugin("base-plugin", "1.0.0", List.of(), Map.of());
        AgentRuntimePlugin extension = plugin(
                "extension-plugin",
                "2.1.0",
                List.of(
                        new RuntimePluginDependency("base-plugin", "^1.0.0", false),
                        new RuntimePluginDependency("optional-plugin", ">=1.0.0", true)
                ),
                Map.of("tools", List.of("EXTENSION_TOOL"))
        );
        when(discovery.plugins()).thenReturn(List.of(base, extension));
        when(toolRegistry.supportingExecutorClasses("EXTENSION_TOOL"))
                .thenReturn(List.of("com.example.ExtensionToolExecutor"));

        var snapshot = new RuntimePluginObservationService(discovery, toolRegistry).snapshot();

        assertThat(snapshot.status()).isEqualTo("UP");
        assertThat(snapshot.pluginCount()).isEqualTo(2);
        assertThat(snapshot.optionalMissingCount()).isEqualTo(1);
        var observed = snapshot.plugins().get(1);
        assertThat(observed.pluginCode()).isEqualTo("extension-plugin");
        assertThat(observed.dependencies())
                .extracting(item -> item.pluginCode() + ":" + item.status())
                .containsExactly("base-plugin:satisfied", "optional-plugin:optional_missing");
        assertThat(observed.components().get("tools").get(0).status()).isEqualTo("registered");
        assertThat(observed.components().get("tools").get(0).implementations())
                .containsExactly("com.example.ExtensionToolExecutor");
    }

    private AgentRuntimePlugin plugin(String code,
                                      String version,
                                      List<RuntimePluginDependency> dependencies,
                                      Map<String, List<String>> components) {
        RuntimePluginManifest manifest = new RuntimePluginManifest(
                code,
                version,
                AgentRuntimePlugin.PLATFORM_API_VERSION,
                "com.example." + code.replace("-", "") + ".Plugin",
                List.of(),
                dependencies,
                components
        );
        return () -> manifest;
    }
}
