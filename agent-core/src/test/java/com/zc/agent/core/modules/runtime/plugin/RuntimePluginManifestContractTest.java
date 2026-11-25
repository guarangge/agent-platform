package com.zc.agent.core.modules.runtime.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginComponentType;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimePluginManifestContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadAndWriteStableManifestContract() throws Exception {
        String manifestJson = """
                {
                  "pluginCode": "example-plugin",
                  "version": "1.0.0",
                  "platformApiVersion": "1",
                  "entrypoint": "com.example.ExampleAgentPlugin",
                  "capabilities": ["filesystem.read"],
                  "dependencies": [
                    {
                      "pluginCode": "base-plugin",
                      "versionConstraint": "^1.0.0",
                      "optional": false
                    }
                  ],
                  "components": {
                    "tools": ["EXAMPLE_TOOL"],
                    "stepHandlers": ["example-step"]
                  }
                }
                """;

        RuntimePluginManifest manifest = objectMapper.readValue(
                manifestJson, RuntimePluginManifest.class);
        String serialized = objectMapper.writeValueAsString(manifest);

        assertThat(manifest.pluginCode()).isEqualTo("example-plugin");
        assertThat(manifest.version()).isEqualTo("1.0.0");
        assertThat(manifest.platformApiVersion()).isEqualTo(AgentRuntimePlugin.PLATFORM_API_VERSION);
        assertThat(manifest.capabilities()).containsExactly("filesystem.read");
        assertThat(manifest.dependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.pluginCode()).isEqualTo("base-plugin");
            assertThat(dependency.versionConstraint()).isEqualTo("^1.0.0");
            assertThat(dependency.optional()).isFalse();
        });
        assertThat(manifest.components().get(RuntimePluginComponentType.TOOLS.code()))
                .containsExactly("EXAMPLE_TOOL");
        assertThat(objectMapper.readTree(serialized)).isEqualTo(objectMapper.readTree(manifestJson));
        assertThatThrownBy(() -> manifest.capabilities().add("network.read"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> manifest.components().put("hooks", List.of("trace")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidOrUnknownManifestDeclarations() {
        assertThatThrownBy(() -> new RuntimePluginManifest(
                "Example Plugin", "1.0", "v1", "ExamplePlugin",
                List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RuntimePluginManifest(
                "example-plugin", "1.0.0", "1", "com.example.ExampleAgentPlugin",
                List.of("filesystem.read", "filesystem.read"), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复值");

        assertThatThrownBy(() -> new RuntimePluginManifest(
                "example-plugin", "1.0.0", "1", "com.example.ExampleAgentPlugin",
                List.of("host.root"), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 Runtime Capability");

        assertThatThrownBy(() -> new RuntimePluginManifest(
                "example-plugin", "1.0.0", "1", "com.example.ExampleAgentPlugin",
                List.of(), List.of(), Map.of("unknownComponents", List.of("UNKNOWN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");

        assertThatThrownBy(() -> new RuntimePluginManifest(
                "example-plugin", "1.0.0-01", "1", "com.example.ExampleAgentPlugin",
                List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void shouldPublishSchemaForAllSupportedComponentTypes() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/META-INF/agent-runtime-plugin.schema.json")) {
            assertThat(input).isNotNull();
            JsonNode schema = objectMapper.readTree(input);

            assertThat(schema.path("$schema").asText())
                    .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.path("required")).hasSize(7);
            JsonNode componentProperties = schema.path("properties")
                    .path("components")
                    .path("properties");
            assertThat(RuntimePluginComponentType.values())
                    .allSatisfy(type -> assertThat(componentProperties.has(type.code())).isTrue());
            assertThat(schema.path("properties").path("capabilities").path("items").path("enum"))
                    .hasSize(9);
        }
    }
}
