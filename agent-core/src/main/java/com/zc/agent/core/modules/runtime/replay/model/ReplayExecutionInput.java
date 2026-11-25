package com.zc.agent.core.modules.runtime.replay.model;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;

import java.util.List;

/**
 * Replay Engine 的只读输入。
 *
 * @param replayMode Replay 模式
 * @param sourceRunCode 原 Run 编码
 * @param fromSequence 来源事件起始序号
 * @param toSequence 来源事件结束序号
 * @param events 已冻结的 Runtime Event
 * @param recordedStepResults 已冻结的步骤结果
 */
public record ReplayExecutionInput(
        ReplayMode replayMode,
        String sourceRunCode,
        long fromSequence,
        long toSequence,
        List<RuntimeEventEnvelope> events,
        List<RecordedStepResult> recordedStepResults
) {
}
