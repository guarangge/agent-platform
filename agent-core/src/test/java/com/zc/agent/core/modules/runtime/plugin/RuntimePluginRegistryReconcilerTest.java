package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.RegistryRuntimePluginClient.ExpectedInstallation;
import com.zc.agent.core.modules.runtime.plugin.RegistryRuntimePluginClient.LoadResultRequest;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.PluginObservation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimePluginRegistryReconcilerTest {

    @Test
    void shouldReportLoadedAndMissingPluginsAsDegraded() {
        RegistryRuntimePluginClient client = mock(RegistryRuntimePluginClient.class);
        RuntimePluginObservationService observationService = mock(RuntimePluginObservationService.class);
        String sha256 = "a".repeat(64);
        when(client.expectedInstallations("prod")).thenReturn(List.of(
                expected(10L, "loaded-plugin", "1.0.0", sha256),
                expected(11L, "missing-plugin", "2.0.0", sha256)
        ));
        when(observationService.plugins()).thenReturn(List.of(
                observed("loaded-plugin", "1.0.0", sha256),
                observed("local-only-plugin", "1.0.0", null)
        ));

        var result = new RuntimePluginRegistryReconciler(client, observationService, "prod")
                .reconcile();

        assertThat(result.status()).isEqualTo("degraded");
        assertThat(result.expectedCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.unmanagedCount()).isEqualTo(1);
        ArgumentCaptor<LoadResultRequest> loaded = ArgumentCaptor.forClass(LoadResultRequest.class);
        ArgumentCaptor<LoadResultRequest> missing = ArgumentCaptor.forClass(LoadResultRequest.class);
        verify(client).reportLoadResult(eq(10L), loaded.capture());
        verify(client).reportLoadResult(eq(11L), missing.capture());
        assertThat(loaded.getValue().loadStatus()).isEqualTo("loaded");
        assertThat(missing.getValue().errorCode()).isEqualTo("PLUGIN_NOT_LOADED");
        verify(observationService).updateRegistrySync(result);
    }

    @Test
    void shouldKeepCoreAvailableWhenRegistryCannotBeReached() {
        RegistryRuntimePluginClient client = mock(RegistryRuntimePluginClient.class);
        RuntimePluginObservationService observationService = mock(RuntimePluginObservationService.class);
        when(client.expectedInstallations("prod")).thenThrow(new IllegalStateException("connection refused"));

        var result = new RuntimePluginRegistryReconciler(client, observationService, "prod")
                .reconcile();

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.messages()).singleElement().asString().contains("connection refused");
        verify(observationService).updateRegistrySync(result);
    }

    @Test
    void shouldRejectUnexpectedPluginVersionAndArtifactDigest() {
        RegistryRuntimePluginClient client = mock(RegistryRuntimePluginClient.class);
        RuntimePluginObservationService observationService = mock(RuntimePluginObservationService.class);
        String expectedSha256 = "a".repeat(64);
        String actualSha256 = "b".repeat(64);
        when(client.expectedInstallations("prod")).thenReturn(List.of(
                expected(20L, "version-plugin", "2.0.0", expectedSha256),
                expected(21L, "digest-plugin", "1.0.0", expectedSha256)
        ));
        when(observationService.plugins()).thenReturn(List.of(
                observed("version-plugin", "1.0.0", expectedSha256),
                observed("digest-plugin", "1.0.0", actualSha256)
        ));

        var result = new RuntimePluginRegistryReconciler(client, observationService, "prod")
                .reconcile();

        ArgumentCaptor<LoadResultRequest> versionResult = ArgumentCaptor.forClass(LoadResultRequest.class);
        ArgumentCaptor<LoadResultRequest> digestResult = ArgumentCaptor.forClass(LoadResultRequest.class);
        verify(client).reportLoadResult(eq(20L), versionResult.capture());
        verify(client).reportLoadResult(eq(21L), digestResult.capture());
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(versionResult.getValue().errorCode()).isEqualTo("PLUGIN_VERSION_MISMATCH");
        assertThat(digestResult.getValue().errorCode()).isEqualTo("PLUGIN_ARTIFACT_SHA256_MISMATCH");
    }

    private ExpectedInstallation expected(Long id, String pluginCode, String version, String sha256) {
        return new ExpectedInstallation(
                id, id, pluginCode, id, version, "1", "file:///plugins/" + pluginCode + ".jar",
                sha256, "prod", "pending_restart");
    }

    private PluginObservation observed(String pluginCode, String version, String sha256) {
        return new PluginObservation(
                pluginCode, version, "1", "com.example.Plugin", "loaded",
                "file:///plugins/" + pluginCode + ".jar", sha256, "AppClassLoader",
                List.of(), List.of(), Map.of());
    }
}
