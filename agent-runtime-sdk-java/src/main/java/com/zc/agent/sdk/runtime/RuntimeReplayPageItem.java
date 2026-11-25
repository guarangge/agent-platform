package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Replay 审计列表项。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run
 * @param sourceTaskId 来源 Task
 * @param sourceTaskTraceId 来源 Task Trace
 * @param userId 用户 ID
 * @param status 状态
 * @param errorMessage 错误信息
 * @param durationMs 耗时
 * @param startedAt 开始时间
 * @param finishedAt 完成时间
 * @param createdAt 创建时间
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeReplayPageItem(
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        Long sourceTaskTraceId,
        Long userId,
        String status,
        String errorMessage,
        Long durationMs,
        String startedAt,
        String finishedAt,
        String createdAt
) {
}
