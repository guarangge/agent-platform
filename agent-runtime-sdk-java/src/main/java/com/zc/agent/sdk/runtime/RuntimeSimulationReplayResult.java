package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 无副作用 Simulation Replay 结果。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 来源 Run
 * @param sourceTaskId 来源 Task
 * @param status 状态
 * @param eventRange 事件范围
 * @param output 回放输出
 * @param injectedObservations 注入的录制 Observation
 * @param eventDiff 事件差异
 * @param artifactVerification Artifact 校验
 * @param manifest 不可变 Manifest
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeSimulationReplayResult(
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        String status,
        Map<String, Object> eventRange,
        Map<String, Object> output,
        List<Map<String, Object>> injectedObservations,
        Map<String, Object> eventDiff,
        Map<String, Object> artifactVerification,
        Map<String, Object> manifest
) {
}
