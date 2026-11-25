package com.zc.agent.core.modules.sandbox.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sandbox 执行审计记录。
 */
public record SandboxExecutionRecord(
        Long executionId,
        String executionCode,
        Long profileId,
        String profileCode,
        String image,
        List<String> command,
        String status,
        String containerId,
        Integer exitCode,
        Boolean timedOut,
        Boolean outputTruncated,
        String stdoutText,
        String stderrText,
        String errorMessage,
        Long inputBytes,
        Long outputBytes,
        Long workspaceBytes,
        Long durationMs,
        Long requestedBy,
        String requestedUsername,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
