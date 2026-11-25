package com.zc.agent.core.modules.sandbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 写入独立工作区的输入文件。
 */
public record SandboxInputFileRequest(
        @NotBlank @Size(max = 512) String path,
        @NotBlank String contentBase64
) {
}
