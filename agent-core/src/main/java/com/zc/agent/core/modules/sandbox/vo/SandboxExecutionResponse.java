package com.zc.agent.core.modules.sandbox.vo;

import com.zc.agent.core.modules.sandbox.model.SandboxExecutionRecord;

import java.util.List;

/**
 * Sandbox 执行结果。
 */
public record SandboxExecutionResponse(
        SandboxExecutionRecord execution,
        List<SandboxArtifactResponse> artifacts
) {
}
