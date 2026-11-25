package com.zc.agent.core.modules.sandbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Sandbox 安全配置保存请求。
 */
public record SandboxProfileRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String profileCode,
        @NotBlank @Size(max = 128) String profileName,
        @NotBlank @Size(max = 255) String image,
        @NotEmpty @Size(max = 32) List<@NotBlank @Size(max = 128) String> allowedCommands,
        @NotBlank @Pattern(regexp = "none|bridge") String networkMode,
        @NotNull @Min(67108864) @Max(4294967296L) Long memoryBytes,
        @NotNull @Min(100000000) @Max(4000000000L) Long nanoCpus,
        @NotNull @Min(16) @Max(1024) Long pidsLimit,
        @NotNull @Min(1) @Max(3600) Integer timeoutSeconds,
        @NotNull @Min(1024) @Max(10485760) Long maxOutputBytes,
        @NotNull @Min(1024) @Max(104857600) Long maxWorkspaceBytes,
        @NotBlank @Pattern(regexp = "enabled|disabled") String status
) {
}
