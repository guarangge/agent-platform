package com.zc.agent.core.modules.sandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.zc.agent.core.modules.sandbox.config.SandboxDockerConfiguration;
import com.zc.agent.core.modules.sandbox.config.SandboxProperties;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerSandboxExecutorIntegrationTest {

    @TempDir
    Path workspace;
    private DockerClient dockerClient;
    private DockerSandboxExecutor executor;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("SANDBOX_INTEGRATION_TEST")),
                "设置 SANDBOX_INTEGRATION_TEST=true 后运行真实 Docker 隔离测试");
        SandboxProperties properties = new SandboxProperties();
        dockerClient = new SandboxDockerConfiguration().sandboxDockerClient(properties);
        executor = new DockerSandboxExecutor(dockerClient, properties);
        executor.verifyDockerConnection();
        assertThat(dockerClient.inspectImageCmd("agent-sandbox-runtime:1.0.0").exec().getId()).isNotBlank();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dockerClient != null) dockerClient.close();
    }

    @Test
    void shouldEnforceNonRootReadonlyRootfsNoNetworkAndCleanupContainer() {
        String script = """
                import os, socket, pathlib
                print('uid=' + str(os.getuid()))
                try:
                    pathlib.Path('/escape.txt').write_text('bad')
                    print('rootfs=writable')
                except Exception:
                    print('rootfs=readonly')
                try:
                    socket.create_connection(('1.1.1.1', 53), timeout=.3)
                    print('network=enabled')
                except Exception:
                    print('network=blocked')
                pathlib.Path('/workspace/result.txt').write_text('isolated')
                """;
        AtomicReference<String> containerId = new AtomicReference<>();

        DockerSandboxExecutor.ExecutionResult result = executor.execute(101L, profile(10, 8192),
                List.of("python3", "-c", script), workspace, id -> {
                    containerId.set(id);
                    var host = dockerClient.inspectContainerCmd(id).exec().getHostConfig();
                    assertThat(host.getReadonlyRootfs()).isTrue();
                    assertThat(host.getMemory()).isEqualTo(134217728L);
                    assertThat(host.getMemorySwap()).isEqualTo(134217728L);
                    assertThat(host.getNanoCPUs()).isEqualTo(500000000L);
                    assertThat(host.getPidsLimit()).isEqualTo(32L);
                    assertThat(host.getNetworkMode()).isEqualTo("none");
                    assertThat(host.getCapDrop()).contains(com.github.dockerjava.api.model.Capability.ALL);
                    assertThat(host.getSecurityOpts()).contains("no-new-privileges:true");
                    return true;
                });

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("uid=65534", "rootfs=readonly", "network=blocked");
        assertThat(workspace.resolve("result.txt")).hasContent("isolated");
        assertThatThrownBy(() -> dockerClient.inspectContainerCmd(containerId.get()).exec()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldTerminateOnTimeoutAndOutputOverflow() {
        DockerSandboxExecutor.ExecutionResult timeout = executor.execute(102L, profile(1, 8192),
                List.of("python3", "-c", "import time; time.sleep(3)"), workspace, ignored -> true);
        DockerSandboxExecutor.ExecutionResult overflow = executor.execute(103L, profile(10, 1024),
                List.of("python3", "-c", "print('x' * 100000)"), workspace, ignored -> true);

        assertThat(timeout.timedOut()).isTrue();
        assertThat(overflow.outputTruncated()).isTrue();
        assertThat(overflow.outputBytes()).isEqualTo(1024);
    }

    @Test
    void shouldRejectCancelledBeforeStartAndTerminateOnWorkspaceOverflow() {
        assertThatThrownBy(() -> executor.execute(104L, profile(10, 8192),
                List.of("python3", "--version"), workspace, ignored -> false))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("启动前取消");

        SandboxProfileRecord limited = new SandboxProfileRecord(1L, "TEST", "测试", "agent-sandbox-runtime:1.0.0",
                List.of("python3"), "none", 134217728L, 500000000L, 32L, 10, 8192L,
                1024L, "enabled", LocalDateTime.now(), LocalDateTime.now());
        DockerSandboxExecutor.ExecutionResult overflow = executor.execute(105L, limited,
                List.of("python3", "-c", "import pathlib,time; pathlib.Path('/workspace/big').write_bytes(b'x'*100000); time.sleep(2)"),
                workspace, ignored -> true);

        assertThat(overflow.workspaceExceeded()).isTrue();
    }

    private SandboxProfileRecord profile(int timeout, long outputLimit) {
        return new SandboxProfileRecord(1L, "TEST", "测试", "agent-sandbox-runtime:1.0.0",
                List.of("python3"), "none", 134217728L, 500000000L, 32L, timeout, outputLimit,
                1048576L, "enabled", LocalDateTime.now(), LocalDateTime.now());
    }
}
