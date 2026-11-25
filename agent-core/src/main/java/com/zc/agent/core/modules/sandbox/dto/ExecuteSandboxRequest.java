package com.zc.agent.core.modules.sandbox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Sandbox 容器执行请求。
 */
public record ExecuteSandboxRequest(
        @NotNull Long profileId,
        @NotEmpty @Size(max = 64) List<@Size(max = 4096) String> command,
        @Size(max = 32) List<@Valid SandboxInputFileRequest> inputFiles,
        @Size(max = 32) List<@Size(max = 512) String> artifactPaths
) {
}
