package com.zc.agent.core.modules.sandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.zc.agent.core.modules.sandbox.config.SandboxProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DockerSandboxExecutorPathTest {

    @Test
    void shouldTranslateContainerWorkspaceToDockerHostPath() {
        SandboxProperties properties = new SandboxProperties();
        properties.setWorkspaceRoot(Path.of("/workspace/sandbox-workspace"));
        properties.setHostWorkspaceRoot("/Users/test/project/.runtime/sandbox-workspace");
        DockerSandboxExecutor executor = new DockerSandboxExecutor(mock(DockerClient.class), properties);

        Path resolved = executor.resolveDockerWorkspace(
                Path.of("/workspace/sandbox-workspace/node-a/SBOX_1-123"));

        assertThat(resolved).isEqualTo(
                Path.of("/Users/test/project/.runtime/sandbox-workspace/node-a/SBOX_1-123"));
    }

    @Test
    void shouldKeepWorkspacePathForHostProcessDeployment() {
        SandboxProperties properties = new SandboxProperties();
        DockerSandboxExecutor executor = new DockerSandboxExecutor(mock(DockerClient.class), properties);
        Path workspace = Path.of(System.getProperty("java.io.tmpdir"), "agent-sandbox", "SBOX_1");

        assertThat(executor.resolveDockerWorkspace(workspace)).isEqualTo(workspace.toAbsolutePath().normalize());
    }
}
