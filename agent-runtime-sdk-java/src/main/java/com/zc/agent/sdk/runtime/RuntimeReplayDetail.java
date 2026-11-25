package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Replay Manifest、输入和结果详情。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run
 * @param sourceTaskId 来源 Task
 * @param status 状态
 * @param manifest 不可变 Manifest
 * @param inputSnapshot 输入快照
 * @param resultSnapshot 结果快照
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeReplayDetail(
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        String status,
        Map<String, Object> manifest,
        Map<String, Object> inputSnapshot,
        Map<String, Object> resultSnapshot
) {
}
