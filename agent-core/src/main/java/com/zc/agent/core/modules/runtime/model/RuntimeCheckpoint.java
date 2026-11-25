package com.zc.agent.core.modules.runtime.model;

import java.util.List;

/**
 * 运行时 Checkpoint 的稳定快照模型。
 *
 * @param id                  Checkpoint ID
 * @param taskId              任务 ID
 * @param taskStepId          当前任务步骤 ID
 * @param status              Checkpoint 状态
 * @param currentStepNo       当前步骤序号
 * @param nextStepNo          下一步骤序号
 * @param plan                计划快照
 * @param decision            决策快照
 * @param results             已完成步骤结果
 * @param traceId             Trace ID
 * @param recoveryDisposition 恢复安全处置
 * @param ambiguityReason     外部副作用不确定原因
 */
public record RuntimeCheckpoint(
        Long id,
        Long taskId,
        Long taskStepId,
        String status,
        Integer currentStepNo,
        Integer nextStepNo,
        ExecutionPlan plan,
        ExecutionDecision decision,
        List<RuntimeStepResultContext> results,
        String traceId,
        String recoveryDisposition,
        String ambiguityReason
) {
}
