package com.zc.agent.core.modules.runtime.replay.vo;

import java.time.LocalDateTime;

/**
 * Replay 管理分页项。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run 编码
 * @param sourceTaskId 来源 Task ID
 * @param sourceTaskTraceId 来源 Task Trace ID
 * @param userId 发起用户 ID
 * @param status 状态
 * @param errorMessage 错误信息
 * @param durationMs 执行耗时毫秒
 * @param startedAt 开始时间
 * @param finishedAt 结束时间
 * @param createdAt 创建时间
 */
public record ReplayPageItemResponse(
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
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
