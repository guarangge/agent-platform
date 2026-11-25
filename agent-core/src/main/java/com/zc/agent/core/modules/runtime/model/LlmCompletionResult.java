package com.zc.agent.core.modules.runtime.model;

/**
 * 标准模型调用结果，统一承载正文、Token usage 和供应商元数据。
 */
public record LlmCompletionResult(
        String content,
        String provider,
        String modelIdentifier,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs
) {
}
