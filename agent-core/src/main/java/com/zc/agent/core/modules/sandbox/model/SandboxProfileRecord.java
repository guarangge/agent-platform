package com.zc.agent.core.modules.sandbox.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sandbox 安全配置记录。
 */
public record SandboxProfileRecord(
        Long profileId,
        String profileCode,
        String profileName,
        String image,
        List<String> allowedCommands,
        String networkMode,
        Long memoryBytes,
        Long nanoCpus,
        Long pidsLimit,
        Integer timeoutSeconds,
        Long maxOutputBytes,
        Long maxWorkspaceBytes,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
