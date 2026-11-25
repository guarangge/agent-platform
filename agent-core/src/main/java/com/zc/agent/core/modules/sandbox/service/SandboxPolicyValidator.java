package com.zc.agent.core.modules.sandbox.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.dto.SandboxInputFileRequest;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sandbox 命令、镜像和工作区路径策略校验。
 */
@Component
public class SandboxPolicyValidator {

    public void validateProfile(SandboxProfileRecord profile) {
        int lastSlash = profile.image().lastIndexOf('/');
        int lastColon = profile.image().lastIndexOf(':');
        boolean digestPinned = profile.image().contains("@sha256:");
        boolean tagPinned = lastColon > lastSlash && lastColon < profile.image().length() - 1;
        if (profile.image().endsWith(":latest") || (!tagPinned && !digestPinned)) {
            throw new BusinessException("Sandbox 镜像必须使用明确版本标签或 sha256 digest，禁止 latest");
        }
        Set<String> commands = new HashSet<>();
        for (String command : profile.allowedCommands()) {
            String normalized = command == null ? "" : command.trim();
            if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) {
                throw new BusinessException("允许命令必须是无路径的可执行文件名");
            }
            if (!commands.add(normalized)) {
                throw new BusinessException("允许命令不能重复：" + normalized);
            }
        }
    }

    public void validateExecution(SandboxProfileRecord profile, List<String> command,
                                  List<SandboxInputFileRequest> inputFiles, List<String> artifactPaths) {
        if (!"enabled".equals(profile.status())) {
            throw new BusinessException("Sandbox 配置未启用");
        }
        validateProfile(profile);
        if (command == null || command.isEmpty() || !profile.allowedCommands().contains(command.get(0))) {
            throw new BusinessException("命令不在 Sandbox 白名单中：" + (command == null || command.isEmpty() ? "" : command.get(0)));
        }
        command.forEach(argument -> {
            if (argument == null || argument.indexOf('\0') >= 0) {
                throw new BusinessException("命令参数包含非法字符");
            }
        });
        if (inputFiles != null) inputFiles.forEach(file -> validateRelativePath(file.path()));
        if (artifactPaths != null) artifactPaths.forEach(this::validateRelativePath);
    }

    public Path resolveWorkspacePath(Path workspace, String relativePath) {
        validateRelativePath(relativePath);
        Path resolved = workspace.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new BusinessException("Sandbox 文件路径越界：" + relativePath);
        }
        return resolved;
    }

    private void validateRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\") || value.indexOf('\0') >= 0) {
            throw new BusinessException("Sandbox 文件路径必须是有效相对路径");
        }
        Path path;
        try { path = Path.of(value).normalize(); }
        catch (RuntimeException exception) { throw new BusinessException("Sandbox 文件路径无效：" + value); }
        if (path.startsWith("..") || path.isAbsolute()) {
            throw new BusinessException("Sandbox 文件路径禁止越界：" + value);
        }
    }
}
