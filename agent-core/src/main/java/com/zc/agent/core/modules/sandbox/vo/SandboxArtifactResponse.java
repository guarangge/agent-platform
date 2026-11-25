package com.zc.agent.core.modules.sandbox.vo;

/**
 * Sandbox 输出产物。
 */
public record SandboxArtifactResponse(
        String path,
        long size,
        String contentBase64
) {
}
