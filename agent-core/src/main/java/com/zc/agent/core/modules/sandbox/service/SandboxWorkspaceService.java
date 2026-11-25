package com.zc.agent.core.modules.sandbox.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.config.SandboxProperties;
import com.zc.agent.core.modules.sandbox.dto.SandboxInputFileRequest;
import com.zc.agent.core.modules.sandbox.vo.SandboxArtifactResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sandbox 独立工作区创建、产物采集与安全清理。
 */
@Component
public class SandboxWorkspaceService {

    private final SandboxProperties properties;
    private final SandboxPolicyValidator policyValidator;
    private Path workspaceRoot;

    public SandboxWorkspaceService(SandboxProperties properties, SandboxPolicyValidator policyValidator) {
        this.properties = properties;
        this.policyValidator = policyValidator;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(properties.getWorkspaceRoot());
        Path baseRoot = properties.getWorkspaceRoot().toRealPath();
        String nodeDirectory = properties.getNodeId().replaceAll("[^A-Za-z0-9_.-]", "_");
        workspaceRoot = baseRoot.resolve(nodeDirectory).normalize();
        Files.createDirectories(workspaceRoot);
        if (properties.isCleanupOnStartup()) {
            try (var stream = Files.list(workspaceRoot)) {
                stream.forEach(this::deleteTree);
            }
        }
    }

    public Workspace create(String executionCode, List<SandboxInputFileRequest> files, long profileLimit) {
        try {
            Path workspace = Files.createTempDirectory(workspaceRoot, executionCode + "-").toRealPath();
            setContainerWritablePermissions(workspace);
            long total = 0;
            if (files != null) {
                for (SandboxInputFileRequest file : files) {
                    byte[] bytes;
                    try { bytes = Base64.getDecoder().decode(file.contentBase64()); }
                    catch (IllegalArgumentException exception) { throw new BusinessException("输入文件不是有效 Base64：" + file.path()); }
                    total = Math.addExact(total, bytes.length);
                    long maximum = Math.min(properties.getMaxInputBytes(), profileLimit);
                    if (total > maximum) throw new BusinessException("Sandbox 输入文件超过工作区限制");
                    Path target = policyValidator.resolveWorkspacePath(workspace, file.path());
                    Files.createDirectories(target.getParent());
                    ensureNoSymbolicLink(workspace, target.getParent());
                    Files.write(target, bytes);
                    setContainerWritablePermissions(target.getParent());
                    setContainerWritablePermissions(target);
                }
            }
            return new Workspace(workspace, total);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("创建 Sandbox 工作区失败", exception);
        }
    }

    public List<SandboxArtifactResponse> collectArtifacts(Path workspace, List<String> paths, long workspaceLimit) {
        if (paths == null || paths.isEmpty()) return List.of();
        List<SandboxArtifactResponse> artifacts = new ArrayList<>();
        long total = 0;
        for (String path : paths) {
            Path target = policyValidator.resolveWorkspacePath(workspace, path);
            try {
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
                ensureNoSymbolicLink(workspace, target);
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BusinessException("Sandbox 产物必须是普通文件：" + path);
                }
                byte[] content = Files.readAllBytes(target);
                total = Math.addExact(total, content.length);
                if (total > Math.min(properties.getMaxArtifactBytes(), workspaceLimit)) {
                    throw new BusinessException("Sandbox 输出产物超过采集限制");
                }
                artifacts.add(new SandboxArtifactResponse(path, content.length, Base64.getEncoder().encodeToString(content)));
            } catch (IOException exception) {
                throw new BusinessException("读取 Sandbox 输出产物失败：" + path, exception);
            }
        }
        return List.copyOf(artifacts);
    }

    public long calculateSize(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) return 0;
        try (var stream = Files.walk(workspace)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .mapToLong(path -> {
                        try { return Files.size(path); } catch (IOException ignored) { return 0; }
                    }).sum();
        } catch (IOException ignored) {
            return 0;
        }
    }

    public void cleanup(Path workspace) {
        if (workspace == null || workspaceRoot == null || !workspace.normalize().startsWith(workspaceRoot)) return;
        deleteTree(workspace);
    }

    private void deleteTree(Path workspace) {
        try (var stream = Files.walk(workspace)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private void ensureNoSymbolicLink(Path workspace, Path target) throws IOException {
        Path current = workspace;
        Path relative = workspace.relativize(target);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new BusinessException("Sandbox 工作区禁止符号链接");
        }
    }

    private void setContainerWritablePermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
            if (Files.isRegularFile(path)) {
                permissions.remove(PosixFilePermission.OWNER_EXECUTE);
                permissions.remove(PosixFilePermission.GROUP_EXECUTE);
                permissions.remove(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            if (!path.toFile().setWritable(true, false)) throw new IOException("无法设置工作区写权限");
        }
    }

    public record Workspace(Path path, long inputBytes) { }
}
