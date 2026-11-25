package com.zc.agent.sdk.runtime;

/**
 * Simulation Replay 冻结事件范围。
 *
 * @param fromSequence 起始事件序号
 * @param toSequence 结束事件序号
 */
public record RuntimeSimulationReplayRequest(
        Long fromSequence,
        Long toSequence
) {
    public static RuntimeSimulationReplayRequest fullRange() {
        return new RuntimeSimulationReplayRequest(null, null);
    }
}
