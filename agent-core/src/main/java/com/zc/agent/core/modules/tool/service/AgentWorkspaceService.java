package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.config.AgentWorkspaceProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 按用户、Agent、会话隔离的持久文件工作区，不暴露宿主任意路径。
 */
@Service
public class AgentWorkspaceService {

    private final AgentWorkspaceProperties properties;
    private Path root;

    public AgentWorkspaceService(AgentWorkspaceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(properties.getRoot());
        root = properties.getRoot().toRealPath();
    }

    public WorkspaceResult execute(ExecutionContext context, String operation, String relativePath,
                                   String content, boolean overwrite) {
        Path workspace = workspace(context);
        Path target = resolve(workspace, relativePath == null ? "." : relativePath);
        return switch (operation) {
            case "list" -> list(workspace, target);
            case "read" -> read(workspace, target);
            case "write" -> write(workspace, target, content, overwrite);
            case "delete" -> delete(workspace, target);
            case "mkdir" -> mkdir(workspace, target);
            default -> throw BuiltinToolSupport.failure("不支持的工作区操作：" + operation,
                    "WORKSPACE_OPERATION_INVALID", Map.of("operation", operation));
        };
    }

    private Path workspace(ExecutionContext context) {
        try {
            Path path = root.resolve("user-" + context.currentUser().userId())
                    .resolve("agent-" + context.agentContext().agentId())
                    .resolve("conversation-" + context.conversationContext().conversationId())
                    .normalize();
            if (!path.startsWith(root)) {
                throw invalidPath();
            }
            Files.createDirectories(path);
            ensureNoSymlink(root, path);
            return path.toRealPath();
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("创建 Agent 工作区失败", "WORKSPACE_INITIALIZE_FAILED", Map.of());
        }
    }

    private WorkspaceResult list(Path workspace, Path target) {
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw BuiltinToolSupport.failure("目标不是目录", "WORKSPACE_NOT_DIRECTORY", Map.of());
        }
        try (var paths = Files.walk(target, 2)) {
            List<FileItem> items = paths.filter(path -> !path.equals(target)).limit(500)
                    .map(path -> new FileItem(workspace.relativize(path).toString(),
                            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), safeSize(path)))
                    .sorted(Comparator.comparing(FileItem::path)).toList();
            return new WorkspaceResult("list", workspace.relativize(target).toString(), null, items, items.size());
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("读取工作区目录失败", "WORKSPACE_LIST_FAILED", Map.of());
        }
    }

    private WorkspaceResult read(Path workspace, Path target) {
        ensureRegularFile(target);
        try {
            long size = Files.size(target);
            if (size > properties.getMaxFileBytes()) {
                throw BuiltinToolSupport.failure("文件超过读取上限", "WORKSPACE_FILE_TOO_LARGE", Map.of("bytes", size));
            }
            String value = Files.readString(target, StandardCharsets.UTF_8);
            return new WorkspaceResult("read", workspace.relativize(target).toString(), value, List.of(), size);
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("读取工作区文件失败", "WORKSPACE_READ_FAILED", Map.of());
        }
    }

    private WorkspaceResult write(Path workspace, Path target, String content, boolean overwrite) {
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.getMaxFileBytes()) {
            throw BuiltinToolSupport.failure("文件超过写入上限", "WORKSPACE_FILE_TOO_LARGE", Map.of("bytes", bytes.length));
        }
        try {
            Files.createDirectories(target.getParent());
            ensureNoSymlink(workspace, target.getParent());
            long currentSize = Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? Files.size(target) : 0L;
            long projectedSize = calculateSize(workspace) - currentSize + bytes.length;
            if (projectedSize > properties.getMaxWorkspaceBytes()) {
                throw BuiltinToolSupport.failure("会话工作区超过总容量限制", "WORKSPACE_QUOTA_EXCEEDED",
                        Map.of("projectedBytes", projectedSize));
            }
            if (overwrite) {
                Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            }
            return new WorkspaceResult("write", workspace.relativize(target).toString(), null, List.of(), bytes.length);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw BuiltinToolSupport.failure("文件已存在，需显式设置 overwrite=true", "WORKSPACE_FILE_EXISTS", Map.of());
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("写入工作区文件失败", "WORKSPACE_WRITE_FAILED", Map.of());
        }
    }

    private WorkspaceResult delete(Path workspace, Path target) {
        if (target.equals(workspace)) {
            throw BuiltinToolSupport.failure("禁止删除会话工作区根目录", "WORKSPACE_ROOT_DELETE_BLOCKED", Map.of());
        }
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return new WorkspaceResult("delete", workspace.relativize(target).toString(), null, List.of(), 0);
            }
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                try (var paths = Files.walk(target)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
                }
            } else {
                Files.delete(target);
            }
            return new WorkspaceResult("delete", workspace.relativize(target).toString(), null, List.of(), 1);
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("删除工作区路径失败", "WORKSPACE_DELETE_FAILED", Map.of());
        }
    }

    private WorkspaceResult mkdir(Path workspace, Path target) {
        try {
            Files.createDirectories(target);
            ensureNoSymlink(workspace, target);
            return new WorkspaceResult("mkdir", workspace.relativize(target).toString(), null, List.of(), 1);
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("创建工作区目录失败", "WORKSPACE_MKDIR_FAILED", Map.of());
        }
    }

    private Path resolve(Path workspace, String relativePath) {
        if (relativePath.isBlank() || relativePath.indexOf('\0') >= 0) {
            throw invalidPath();
        }
        Path relative;
        try {
            relative = Path.of(relativePath).normalize();
        } catch (RuntimeException exception) {
            throw invalidPath();
        }
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw invalidPath();
        }
        Path target = workspace.resolve(relative).normalize();
        if (!target.startsWith(workspace)) {
            throw invalidPath();
        }
        try {
            ensureNoSymlink(workspace, Files.exists(target) ? target : target.getParent());
        } catch (IOException exception) {
            throw invalidPath();
        }
        return target;
    }

    private void ensureRegularFile(Path target) {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw BuiltinToolSupport.failure("工作区文件不存在或不是普通文件", "WORKSPACE_FILE_NOT_FOUND", Map.of());
        }
    }

    private void ensureNoSymlink(Path base, Path target) throws IOException {
        if (target == null) {
            return;
        }
        Path current = base;
        for (Path segment : base.relativize(target.normalize())) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw invalidPath();
            }
        }
    }

    private long calculateSize(Path workspace) throws IOException {
        try (var paths = Files.walk(workspace)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .mapToLong(this::safeSize).sum();
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw BuiltinToolSupport.failure("删除工作区路径失败", "WORKSPACE_DELETE_FAILED", Map.of());
        }
    }

    private RuntimeException invalidPath() {
        return BuiltinToolSupport.failure("工作区路径无效或越界", "WORKSPACE_PATH_INVALID", Map.of());
    }

    public record WorkspaceResult(String operation, String path, String content, List<FileItem> items, long affected) {
    }

    public record FileItem(String path, boolean directory, long bytes) {
    }
}
