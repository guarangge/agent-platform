package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.util.List;

/**
 * @param assistantContent 最终回复或确定性停止说明
 * @param stopReason 稳定停止原因
 * @param budgetSnapshot 最终安全预算快照
 * @param observations Tool Observation 列表
 */
public record InteractiveLoopResult(
        String assistantContent,
        InteractiveLoopStopReason stopReason,
        InteractiveLoopBudgetState.Snapshot budgetSnapshot,
        List<InteractiveLoopObservation> observations
) {
}
