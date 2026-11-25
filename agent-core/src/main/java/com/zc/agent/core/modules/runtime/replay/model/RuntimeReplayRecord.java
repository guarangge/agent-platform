package com.zc.agent.core.modules.runtime.replay.model;

import java.time.LocalDateTime;

/**
 * Runtime Replay 持久化查询记录。
 *
 * @param id 主键
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run 编码
 * @param sourceTaskId 来源 Task ID
 * @param sourceTaskTraceId 来源 Task Trace ID
 * @param userId 发起用户 ID
 * @param status 状态
 * @param manifestJson Manifest JSON
 * @param inputSnapshotJson 输入快照 JSON
 * @param resultSnapshotJson 结果快照 JSON
 * @param errorMessage 错误信息
 * @param startedAt 开始时间
 * @param finishedAt 结束时间
 * @param createdAt 创建时间
 */
public record RuntimeReplayRecord(
        Long id,
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        Long sourceTaskTraceId,
        Long userId,
        String status,
        String manifestJson,
        String inputSnapshotJson,
        String resultSnapshotJson,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
