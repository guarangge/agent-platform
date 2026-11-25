package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePluginDiscoveryTest {

    @TempDir
    Path pluginRoot;

    @Test
    void shouldDiscoverServiceProviderFromPluginClasspath() throws Exception {
        Path serviceFile = pluginRoot.resolve("META-INF/services/")
                .resolve(AgentRuntimePlugin.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, TestRuntimePlugin.class.getName(), StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{pluginRoot.toUri().toURL()}, getClass().getClassLoader())) {
            RuntimePluginDiscovery discovery = new RuntimePluginDiscovery(classLoader);

            assertThat(discovery.plugins()).singleElement().satisfies(plugin -> {
                assertThat(plugin).isInstanceOf(TestRuntimePlugin.class);
                assertThat(plugin.manifest().pluginCode()).isEqualTo("test-runtime-plugin");
            });
        }
    }

    public static class TestRuntimePlugin implements AgentRuntimePlugin {

        private static final RuntimePluginManifest MANIFEST = new RuntimePluginManifest(
                "test-runtime-plugin",
                "1.0.0",
                PLATFORM_API_VERSION,
                TestRuntimePlugin.class.getName(),
                List.of(),
                List.of(),
                Map.of("tools", List.of("TEST_TOOL"))
        );

        @Override
        public RuntimePluginManifest manifest() {
            return MANIFEST;
        }
    }
}
