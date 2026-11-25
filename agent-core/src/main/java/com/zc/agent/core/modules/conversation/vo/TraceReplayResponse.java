package com.zc.agent.core.modules.conversation.vo;

import java.util.Map;

/**
 * Trace Prompt 回放结果。
 *
 * @param sourceTaskTraceId 原 Trace 事件 ID
 * @param traceId          原链路 Trace ID
 * @param modelCode        回放模型编码
 * @param modelIdentifier  回放模型标识
 * @param originalOutput   原执行输出
 * @param replayOutput     回放输出
 * @param promptTokens     输入 Token 数
 * @param completionTokens 输出 Token 数
 * @param totalTokens      总 Token 数
 * @param durationMs       回放耗时
 * @param diff             输出差异摘要
 * @param replayCode       独立 Replay 编码
 * @param replayMode       Replay 模式
 * @param replayTraceId    独立 Replay Trace ID
 * @param status           Replay 状态
 * @param manifest         不可变 Replay Manifest
 */
public record TraceReplayResponse(
        Long sourceTaskTraceId,
        String traceId,
        String modelCode,
        String modelIdentifier,
        String originalOutput,
        String replayOutput,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs,
        Map<String, Object> diff,
        String replayCode,
        String replayMode,
        String replayTraceId,
        String status,
        Map<String, Object> manifest
) {
}
