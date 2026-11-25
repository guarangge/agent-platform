package com.zc.agent.registry.modules.runtimeplugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.runtimeplugin.dto.CreateRuntimePluginVersionRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginDependencyRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallationStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginLoadResultRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginVersionStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.UpdateAgentRuntimePluginsRequest;
import com.zc.agent.registry.modules.runtimeplugin.repository.RuntimePluginRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimePluginServiceTest {

    private static final String SHA256 = "a".repeat(64);

    private final RuntimePluginRepository repository = mock(RuntimePluginRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuntimePluginService service = new RuntimePluginService(repository, objectMapper);

    @Test
    void shouldCreateImmutableManifestVersion() throws Exception {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));
        when(repository.pluginExistsByCode("base-plugin")).thenReturn(true);
        when(repository.insertVersion(
                eq(1L), eq("1.2.3"), eq("1"), eq("com.example.ExamplePlugin"),
                anyString(), eq("maven://com.example/example-plugin/1.2.3"), eq(SHA256),
                eq("首个版本"), eq("published"), eq(9L))).thenReturn(2L);

        var result = service.createVersion(1L, versionRequest("^1.0.0"), 9L);

        assertThat(result.path("versionId").asLong()).isEqualTo(2L);
        ArgumentCaptor<String> manifestCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertVersion(
                eq(1L), eq("1.2.3"), eq("1"), eq("com.example.ExamplePlugin"),
                manifestCaptor.capture(), eq("maven://com.example/example-plugin/1.2.3"),
                eq(SHA256), eq("首个版本"), eq("published"), eq(9L));
        var manifest = objectMapper.readTree(manifestCaptor.getValue());
        assertThat(manifest.path("pluginCode").asText()).isEqualTo("example-plugin");
        assertThat(manifest.path("dependencies").get(0).path("versionConstraint").asText())
                .isEqualTo("^1.0.0");
        assertThat(manifest.path("components").path("tools").get(0).asText())
                .isEqualTo("EXAMPLE_TOOL");
    }

    @Test
    void shouldRejectUnsupportedDependencyConstraintBeforePersistingVersion() {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));

        assertThatThrownBy(() -> service.createVersion(1L, versionRequest(">=1.0"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本约束不合法");
    }

    @Test
    void shouldInstallPublishedVersionAsPendingRestart() {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));
        when(repository.findVersion(1L, 2L)).thenReturn(Optional.of(Map.of(
                "id", 2L, "version", "1.2.3", "status", "published")));
        when(repository.findInstallationByEnvironment(1L, "prod")).thenReturn(Optional.empty());
        when(repository.upsertInstallation(1L, 2L, "prod", "enabled", "{}", 9L))
                .thenReturn(3L);

        var result = service.install(1L, new RuntimePluginInstallRequest(
                2L, "prod", "enabled", objectMapper.createObjectNode()), 9L);

        assertThat(result.path("installationId").asLong()).isEqualTo(3L);
        assertThat(result.path("loadStatus").asText()).isEqualTo("pending_restart");
        verify(repository).insertAudit(eq(1L), eq(2L), eq(3L), eq(null), eq("install"),
                eq("{}"), anyString(), eq("success"), anyString(), eq(9L));
    }

    @Test
    void shouldPersistCorePluginLoadResultAndAuditIt() {
        when(repository.findRuntimeInstallationsById(3L)).thenReturn(Optional.of(Map.of(
                "installation_id", 3L,
                "plugin_id", 1L,
                "version_id", 2L,
                "plugin_code", "example-plugin",
                "load_status", "pending_restart"
        )));
        when(repository.updateLoadResult(
                3L, "loaded", "example-plugin", "1.2.3", SHA256, null)).thenReturn(1);

        service.reportLoadResult(3L, new RuntimePluginLoadResultRequest(
                "loaded", "example-plugin", "1.2.3", SHA256, null, null));

        verify(repository).updateLoadResult(
                3L, "loaded", "example-plugin", "1.2.3", SHA256, null);
        verify(repository).insertLoadResultAudit(eq(3L), anyString(), anyString(),
                eq("agent-core 已回写插件加载结果"));
    }

    @Test
    void shouldReplaceAgentBindingsWithEnabledPublishedInstallation() {
        when(repository.agentExists(7L)).thenReturn(true);
        when(repository.findBindingCandidate(3L)).thenReturn(Optional.of(Map.of(
                "installation_id", 3L,
                "plugin_id", 1L,
                "plugin_code", "example-plugin",
                "version_id", 2L,
                "version", "1.2.3",
                "environment_code", "prod",
                "status", "enabled",
                "load_status", "pending_restart"
        )));
        Map<String, Object> bound = Map.of(
                "agent_id", 7L,
                "plugin_id", 1L,
                "installation_id", 3L,
                "version_id", 2L,
                "environment_code", "prod",
                "required", true
        );
        when(repository.findAgentBindings(7L)).thenReturn(List.of(), List.of(bound));
        when(repository.findAvailableInstallations()).thenReturn(List.of());

        var result = service.updateAgentBindings(7L, new UpdateAgentRuntimePluginsRequest(List.of(
                new UpdateAgentRuntimePluginsRequest.AgentRuntimePluginBindingRequest(3L, true)
        )), 9L);

        assertThat(result.path("bindings")).hasSize(1);
        verify(repository).softDeleteAgentBindings(7L, 9L);
        verify(repository).insertAgentBinding(7L, 1L, 3L, true, 9L);
        verify(repository).insertAudit(eq(1L), eq(2L), eq(3L), eq(7L), eq("bind_agent"),
                eq("{}"), anyString(), eq("success"), anyString(), eq(9L));
    }

    @Test
    void shouldRejectDisablingInstallationStillBoundByAgent() {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));
        when(repository.findInstallation(1L, 3L)).thenReturn(Optional.of(Map.of(
                "id", 3L,
                "plugin_id", 1L,
                "version_id", 2L,
                "environment_code", "prod",
                "status", "enabled",
                "load_status", "loaded"
        )));
        when(repository.countAgentBindings(3L)).thenReturn(1);

        assertThatThrownBy(() -> service.updateInstallationStatus(
                1L, 3L, new RuntimePluginInstallationStatusRequest("disabled"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先解除绑定");
        verify(repository, never()).updateInstallationStatus(1L, 3L, "disabled", 9L);
    }

    @Test
    void shouldRejectReplacingInstallationStillBoundByAgent() {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));
        when(repository.findVersion(1L, 4L)).thenReturn(Optional.of(Map.of(
                "id", 4L, "version", "2.0.0", "status", "published")));
        when(repository.findInstallationByEnvironment(1L, "prod")).thenReturn(Optional.of(Map.of(
                "id", 3L,
                "plugin_id", 1L,
                "version_id", 2L,
                "environment_code", "prod",
                "status", "enabled",
                "load_status", "loaded"
        )));
        when(repository.countAgentBindings(3L)).thenReturn(1);

        assertThatThrownBy(() -> service.install(1L, new RuntimePluginInstallRequest(
                4L, "prod", "enabled", objectMapper.createObjectNode()), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先解除绑定");
        verify(repository, never()).upsertInstallation(
                1L, 4L, "prod", "enabled", "{}", 9L);
    }

    @Test
    void shouldRejectDeprecatingVersionStillUsedByEnabledInstallation() {
        when(repository.findPlugin(1L)).thenReturn(Optional.of(Map.of(
                "id", 1L, "plugin_code", "example-plugin", "status", "enabled")));
        when(repository.findVersion(1L, 2L)).thenReturn(Optional.of(Map.of(
                "id", 2L, "version", "1.2.3", "status", "published")));
        when(repository.countActiveInstallations(2L)).thenReturn(1);

        assertThatThrownBy(() -> service.updateVersionStatus(
                1L, 2L, new RuntimePluginVersionStatusRequest("deprecated"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已启用安装");
        verify(repository, never()).updateVersionStatus(1L, 2L, "deprecated", 9L);
    }

    private CreateRuntimePluginVersionRequest versionRequest(String constraint) {
        return new CreateRuntimePluginVersionRequest(
                "1.2.3",
                "1",
                "com.example.ExamplePlugin",
                List.of("filesystem.read"),
                List.of(new RuntimePluginDependencyRequest("base-plugin", constraint, false)),
                Map.of("tools", List.of("EXAMPLE_TOOL")),
                "maven://com.example/example-plugin/1.2.3",
                SHA256,
                "首个版本",
                "published"
        );
    }
}
