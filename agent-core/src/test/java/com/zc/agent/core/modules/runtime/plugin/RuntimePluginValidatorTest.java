package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginDependency;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimePluginValidatorTest {

    private final RuntimePluginValidator validator = new RuntimePluginValidator();

    @Test
    void shouldAcceptCompatibleDependenciesAndMissingOptionalDependency() {
        AgentRuntimePlugin base = plugin(
                "base-plugin", "1.4.0", "1", List.of(), Map.of("policies", List.of("base-policy")));
        AgentRuntimePlugin extension = plugin(
                "extension-plugin",
                "2.0.0",
                "1",
                List.of(
                        new RuntimePluginDependency("base-plugin", "^1.2.0", false),
                        new RuntimePluginDependency("optional-plugin", "1.x", true)
                ),
                Map.of("tools", List.of("EXT_TOOL"))
        );

        assertThatCode(() -> validator.validate(List.of(base, extension)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectIncompatiblePlatformApiVersion() {
        AgentRuntimePlugin plugin = plugin(
                "future-plugin", "1.0.0", "2", List.of(), Map.of());

        assertValidationError(
                List.of(plugin), "PLUGIN_API_VERSION_INCOMPATIBLE", "future-plugin");
    }

    @Test
    void shouldRejectManifestEntrypointMismatch() {
        AgentRuntimePlugin plugin = new TestPlugin(
                "entrypoint-plugin", "1.0.0", "1", List.of(), Map.of(), "com.example.OtherPlugin");

        assertValidationError(
                List.of(plugin), "PLUGIN_ENTRYPOINT_MISMATCH", "entrypoint-plugin");
    }

    @Test
    void shouldRejectDuplicatePluginCodeAndComponentOwnership() {
        AgentRuntimePlugin first = plugin(
                "first-plugin", "1.0.0", "1", List.of(), Map.of("tools", List.of("SHARED_TOOL")));
        AgentRuntimePlugin duplicateCode = plugin(
                "first-plugin", "2.0.0", "1", List.of(), Map.of("tools", List.of("OTHER_TOOL")));
        AgentRuntimePlugin second = plugin(
                "second-plugin", "1.0.0", "1", List.of(), Map.of("tools", List.of("SHARED_TOOL")));

        assertValidationError(
                List.of(first, duplicateCode), "PLUGIN_CODE_CONFLICT", "first-plugin");
        assertValidationError(
                List.of(first, second), "PLUGIN_COMPONENT_CONFLICT", "SHARED_TOOL");
    }

    @Test
    void shouldRejectMissingOrIncompatibleRequiredDependency() {
        AgentRuntimePlugin missing = plugin(
                "missing-client",
                "1.0.0",
                "1",
                List.of(new RuntimePluginDependency("base-plugin", "^1.0.0", false)),
                Map.of()
        );
        AgentRuntimePlugin base = plugin(
                "base-plugin", "2.0.0", "1", List.of(), Map.of());

        assertValidationError(
                List.of(missing), "PLUGIN_DEPENDENCY_MISSING", "base-plugin");
        assertValidationError(
                List.of(base, missing), "PLUGIN_DEPENDENCY_VERSION_MISMATCH", "base-plugin");
    }

    @Test
    void shouldRejectInvalidConstraintAndDependencyCycle() {
        AgentRuntimePlugin invalidConstraint = plugin(
                "invalid-client",
                "1.0.0",
                "1",
                List.of(new RuntimePluginDependency("base-plugin", ">=1.0", false)),
                Map.of()
        );
        AgentRuntimePlugin base = plugin(
                "base-plugin", "1.0.0", "1", List.of(), Map.of());
        AgentRuntimePlugin first = plugin(
                "cycle-first",
                "1.0.0",
                "1",
                List.of(new RuntimePluginDependency("cycle-second", "1.0.0", false)),
                Map.of()
        );
        AgentRuntimePlugin second = plugin(
                "cycle-second",
                "1.0.0",
                "1",
                List.of(new RuntimePluginDependency("cycle-first", "1.0.0", false)),
                Map.of()
        );

        assertValidationError(
                List.of(base, invalidConstraint), "PLUGIN_VERSION_CONSTRAINT_INVALID", ">=1.0");
        assertValidationError(
                List.of(first, second), "PLUGIN_DEPENDENCY_CYCLE", "cycle-first");
    }

    private AgentRuntimePlugin plugin(String pluginCode,
                                      String version,
                                      String platformApiVersion,
                                      List<RuntimePluginDependency> dependencies,
                                      Map<String, List<String>> components) {
        return new TestPlugin(
                pluginCode, version, platformApiVersion, dependencies, components, TestPlugin.class.getName());
    }

    private void assertValidationError(List<AgentRuntimePlugin> plugins,
                                       String errorCode,
                                       String messagePart) {
        assertThatThrownBy(() -> validator.validate(plugins))
                .isInstanceOfSatisfying(RuntimePluginValidationException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(errorCode);
                    assertThat(exception).hasMessageContaining(messagePart);
                    assertThat(exception.details()).isNotEmpty();
                });
    }

    private static class TestPlugin implements AgentRuntimePlugin {

        private final RuntimePluginManifest manifest;

        private TestPlugin(String pluginCode,
                           String version,
                           String platformApiVersion,
                           List<RuntimePluginDependency> dependencies,
                           Map<String, List<String>> components,
                           String entrypoint) {
            this.manifest = new RuntimePluginManifest(
                    pluginCode,
                    version,
                    platformApiVersion,
                    entrypoint,
                    List.of(),
                    dependencies,
                    components
            );
        }

        @Override
        public RuntimePluginManifest manifest() {
            return manifest;
        }
    }
}
