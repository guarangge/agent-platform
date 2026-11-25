package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin;
import com.zc.agent.core.modules.runtime.plugin.RuntimePluginValidationException;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginComponentType;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutorRegistryTest {

    @Test
    void shouldResolveSingleExtensionDeterministically() {
        ToolExecutor executor = executor("EXT_TOOL");
        ToolExecutorRegistry registry = new ToolExecutorRegistry(List.of(executor));

        assertThat(registry.resolve("EXT_TOOL")).isSameAs(executor);
    }

    @Test
    void shouldRejectDuplicateToolCodeInsteadOfUsingBeanOrder() {
        ToolExecutorRegistry registry = new ToolExecutorRegistry(List.of(executor("EXT_TOOL"), executor("EXT_TOOL")));

        assertThatThrownBy(() -> registry.resolve("EXT_TOOL"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("注册冲突");
    }

    @Test
    void shouldExposePluginOwnershipWithoutChangingExecutorResolution() {
        ToolExecutor executor = executor("EXT_TOOL");
        AgentRuntimePlugin plugin = () -> new RuntimePluginManifest(
                "example-plugin",
                "1.0.0",
                AgentRuntimePlugin.PLATFORM_API_VERSION,
                "com.example.ExampleAgentPlugin",
                List.of(),
                List.of(),
                Map.of(RuntimePluginComponentType.TOOLS.code(), List.of("EXT_TOOL"))
        );
        ToolExecutorRegistry registry = new ToolExecutorRegistry(
                List.of(executor), List.of(plugin));

        assertThat(registry.resolve("EXT_TOOL")).isSameAs(executor);
        assertThat(registry.registeredPluginTools())
                .containsEntry("EXT_TOOL", List.of("example-plugin"));
        assertThatThrownBy(() -> registry.registeredPluginTools()
                .put("OTHER_TOOL", List.of("example-plugin")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectMissingOrConflictingPluginToolExecutorAtStartup() {
        AgentRuntimePlugin plugin = () -> new RuntimePluginManifest(
                "example-plugin",
                "1.0.0",
                AgentRuntimePlugin.PLATFORM_API_VERSION,
                "com.example.ExampleAgentPlugin",
                List.of(),
                List.of(),
                Map.of(RuntimePluginComponentType.TOOLS.code(), List.of("EXT_TOOL"))
        );

        assertThatThrownBy(() -> new ToolExecutorRegistry(List.of(), List.of(plugin)))
                .isInstanceOfSatisfying(RuntimePluginValidationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo("PLUGIN_TOOL_EXECUTOR_MISSING"));
        assertThatThrownBy(() -> new ToolExecutorRegistry(
                List.of(executor("EXT_TOOL"), executor("EXT_TOOL")), List.of(plugin)))
                .isInstanceOfSatisfying(RuntimePluginValidationException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo("PLUGIN_TOOL_EXECUTOR_CONFLICT"));
    }

    private ToolExecutor executor(String supportedCode) {
        return new ToolExecutor() {
            @Override
            public boolean supports(String toolCode) {
                return supportedCode.equals(toolCode);
            }

            @Override
            public ToolExecutionResult execute(ExecutionContext executionContext,
                                               RegistryAgentTool tool,
                                               String toolInputText) {
                return null;
            }
        };
    }
}
