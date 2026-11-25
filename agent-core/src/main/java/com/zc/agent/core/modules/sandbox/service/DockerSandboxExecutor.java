package com.zc.agent.core.modules.sandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.config.SandboxProperties;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Docker Engine API 的强隔离 Sandbox 执行器。
 */
@Slf4j
@Component
public class DockerSandboxExecutor {

    private static final String MANAGED_LABEL = "agent.sandbox.managed";
    private static final String NODE_LABEL = "agent.sandbox.node";
    private final DockerClient dockerClient;
    private final SandboxProperties properties;
    private final Map<Long, String> runningContainers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService workspaceMonitor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "sandbox-workspace-monitor");
        thread.setDaemon(true);
        return thread;
    });

    public DockerSandboxExecutor(DockerClient sandboxDockerClient, SandboxProperties properties) {
        this.dockerClient = sandboxDockerClient;
        this.properties = properties;
    }

    @PostConstruct
    public void verifyDockerConnection() {
        try {
            dockerClient.pingCmd().exec();
            cleanupOrphanContainers();
        } catch (RuntimeException exception) {
            log.warn("Sandbox Docker Engine 暂不可用，执行请求将明确失败: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        workspaceMonitor.shutdownNow();
    }

    public ExecutionResult execute(Long executionId, SandboxProfileRecord profile, List<String> command, Path workspace,
                                   ContainerCreatedCallback callback) {
        Instant started = Instant.now();
        String containerId = null;
        LimitedLogCollector logs = null;
        ScheduledFuture<?> workspaceLimitTask = null;
        AtomicBoolean workspaceExceeded = new AtomicBoolean();
        try {
            Path dockerWorkspace = resolveDockerWorkspace(workspace);
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(new Bind(dockerWorkspace.toString(), new Volume("/workspace"), AccessMode.rw))
                    .withReadonlyRootfs(true)
                    .withMemory(profile.memoryBytes())
                    .withMemorySwap(profile.memoryBytes())
                    .withNanoCPUs(profile.nanoCpus())
                    .withPidsLimit(profile.pidsLimit())
                    .withNetworkMode(profile.networkMode())
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges:true"))
                    .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,nodev,size=64m"));
            containerId = dockerClient.createContainerCmd(profile.image())
                    .withEntrypoint(new String[0])
                    .withCmd(command)
                    .withWorkingDir("/workspace")
                    .withUser("65534:65534")
                    .withHostConfig(hostConfig)
                    .withLabels(Map.of(MANAGED_LABEL, "true", NODE_LABEL, safeNodeId(),
                            "agent.sandbox.execution", String.valueOf(executionId)))
                    .exec().getId();
            runningContainers.put(executionId, containerId);
            if (!callback.created(containerId)) {
                throw new BusinessException("Sandbox 执行已在启动前取消");
            }
            String activeContainerId = containerId;
            logs = new LimitedLogCollector(profile.maxOutputBytes(), () -> killQuietly(activeContainerId));
            dockerClient.startContainerCmd(containerId).exec();
            workspaceLimitTask = workspaceMonitor.scheduleAtFixedRate(() -> {
                if (workspaceSize(workspace) > profile.maxWorkspaceBytes() && workspaceExceeded.compareAndSet(false, true)) {
                    killQuietly(activeContainerId);
                }
            }, 100, 100, TimeUnit.MILLISECONDS);
            ResultCallback.Adapter<Frame> logCallback = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true).withStdErr(true).withFollowStream(true).withTailAll()
                    .exec(logs);
            Integer exitCode;
            boolean timedOut = false;
            try {
                exitCode = dockerClient.waitContainerCmd(containerId).exec(new WaitContainerResultCallback())
                        .awaitStatusCode(profile.timeoutSeconds(), TimeUnit.SECONDS);
                if (exitCode == null) {
                    timedOut = true;
                    killQuietly(containerId);
                }
            } catch (RuntimeException exception) {
                timedOut = Duration.between(started, Instant.now()).toSeconds() >= profile.timeoutSeconds();
                killQuietly(containerId);
                exitCode = null;
            }
            try { logCallback.awaitCompletion(2, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return new ExecutionResult(exitCode, timedOut, logs.truncated(), workspaceExceeded.get(), logs.stdout(), logs.stderr(),
                    logs.totalBytes(), Duration.between(started, Instant.now()).toMillis());
        } catch (RuntimeException exception) {
            throw new BusinessException("Docker Sandbox 执行失败：" + safeMessage(exception), exception);
        } finally {
            if (workspaceLimitTask != null) workspaceLimitTask.cancel(true);
            runningContainers.remove(executionId);
            removeQuietly(containerId);
        }
    }

    private void cleanupOrphanContainers() {
        if (!properties.isCleanupOnStartup()) return;
        try {
            dockerClient.listContainersCmd().withShowAll(true)
                    .withLabelFilter(Map.of(MANAGED_LABEL, "true", NODE_LABEL, safeNodeId())).exec()
                    .forEach(container -> removeQuietly(container.getId()));
        } catch (RuntimeException exception) {
            log.warn("清理遗留 Sandbox 容器失败: {}", exception.getMessage());
        }
    }

    /**
     * Docker Socket 部署时 Docker Engine 位于宿主机，需要把容器内工作区映射回宿主机真实路径。
     */
    Path resolveDockerWorkspace(Path workspace) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        String hostWorkspaceRoot = properties.getHostWorkspaceRoot();
        if (hostWorkspaceRoot == null || hostWorkspaceRoot.isBlank()) {
            return normalizedWorkspace;
        }
        Path containerRoot = properties.getWorkspaceRoot().toAbsolutePath().normalize();
        if (!normalizedWorkspace.startsWith(containerRoot)) {
            throw new BusinessException("Sandbox 工作区不在配置根目录内：" + normalizedWorkspace);
        }
        return Path.of(hostWorkspaceRoot).toAbsolutePath().normalize()
                .resolve(containerRoot.relativize(normalizedWorkspace))
                .normalize();
    }

    private long workspaceSize(Path workspace) {
        try (var stream = Files.walk(workspace)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).mapToLong(path -> {
                try { return Files.size(path); } catch (Exception ignored) { return 0; }
            }).sum();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String safeNodeId() {
        return properties.getNodeId().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public boolean cancel(Long executionId) {
        String containerId = runningContainers.get(executionId);
        if (containerId == null) return false;
        killQuietly(containerId);
        return true;
    }

    private void killQuietly(String containerId) {
        if (containerId == null) return;
        try { dockerClient.killContainerCmd(containerId).exec(); }
        catch (RuntimeException ignored) { }
    }

    private void removeQuietly(String containerId) {
        if (containerId == null) return;
        try { dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec(); }
        catch (RuntimeException exception) { log.warn("清理 Sandbox 容器失败 containerId={}, error={}", containerId, exception.getMessage()); }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    public interface ContainerCreatedCallback { boolean created(String containerId); }

    public record ExecutionResult(Integer exitCode, boolean timedOut, boolean outputTruncated, boolean workspaceExceeded,
                                  String stdout, String stderr, long outputBytes, long durationMs) { }

    private static final class LimitedLogCollector extends ResultCallback.Adapter<Frame> {
        private final long limit;
        private final Runnable overflowAction;
        private final AtomicLong total = new AtomicLong();
        private final AtomicBoolean truncated = new AtomicBoolean();
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private LimitedLogCollector(long limit, Runnable overflowAction) {
            this.limit = limit;
            this.overflowAction = overflowAction;
        }

        @Override
        public synchronized void onNext(Frame frame) {
            byte[] payload = frame.getPayload();
            long before = total.getAndAdd(payload.length);
            int writable = (int) Math.max(0, Math.min(payload.length, limit - before));
            if (writable > 0) {
                ByteArrayOutputStream target = frame.getStreamType() == StreamType.STDERR ? stderr : stdout;
                target.write(payload, 0, writable);
            }
            if (before + payload.length > limit && truncated.compareAndSet(false, true)) overflowAction.run();
            super.onNext(frame);
        }

        private boolean truncated() { return truncated.get(); }
        private long totalBytes() { return Math.min(total.get(), limit); }
        private String stdout() { return stdout.toString(StandardCharsets.UTF_8); }
        private String stderr() { return stderr.toString(StandardCharsets.UTF_8); }
    }
}
