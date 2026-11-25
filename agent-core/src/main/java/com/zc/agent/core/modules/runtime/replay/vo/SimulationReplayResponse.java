package com.zc.agent.core.modules.runtime.replay.vo;

import com.zc.agent.core.modules.runtime.replay.model.ReplayEventDiff;
import com.zc.agent.core.modules.runtime.replay.model.ArtifactVerificationReport;

import java.util.List;
import java.util.Map;

/**
 * @param replayCode 独立 Replay 编码
 * @param replayTraceId 独立 Replay Trace ID
 * @param replayMode Replay 模式
 * @param sourceRunCode 原 Run 编码
 * @param sourceTaskId 原 Task ID
 * @param status Replay 状态
 * @param eventRange 冻结事件范围
 * @param output 纯内存重组结果
 * @param injectedObservations 实际注入的录制观察
 * @param eventDiff 结构化事件与结果差异
 * @param artifactVerification Artifact 内容哈希与元数据校验报告
 * @param manifest 不可变 Replay Manifest
 */
public record SimulationReplayResponse(
        String replayCode,
        String replayTraceId,
        String replayMode,
        String sourceRunCode,
        Long sourceTaskId,
        String status,
        Map<String, Object> eventRange,
        Map<String, Object> output,
        List<Map<String, Object>> injectedObservations,
        ReplayEventDiff eventDiff,
        ArtifactVerificationReport artifactVerification,
        Map<String, Object> manifest
) {
}
