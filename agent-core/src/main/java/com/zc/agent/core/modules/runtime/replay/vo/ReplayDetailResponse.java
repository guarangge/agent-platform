package com.zc.agent.core.modules.runtime.replay.vo;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Replay 管理详情。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run 编码
 * @param sourceTaskId 来源 Task ID
 * @param sourceTaskTraceId 来源 Task Trace ID
 * @param userId 发起用户 ID
 * @param status 状态
 * @param manifest 不可变 Manifest
 * @param inputSnapshot 不可变输入快照
 * @param resultSnapshot 独立结果快照
 * @param errorMessage 错误信息
 * @param startedAt 开始时间
 * @param finishedAt 结束时间
 * @param createdAt 创建时间
 */
public record ReplayDetailResponse(
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        Long sourceTaskTraceId,
        Long userId,
        String status,
        Map<String, Object> manifest,
        Map<String, Object> inputSnapshot,
        Map<String, Object> resultSnapshot,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
