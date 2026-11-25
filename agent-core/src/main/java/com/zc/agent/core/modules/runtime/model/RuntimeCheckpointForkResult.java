package com.zc.agent.core.modules.runtime.model;

/**
 * 从不可变 Checkpoint 创建独立恢复分支的稳定结果。
 *
 * @param taskId             分支任务 ID
 * @param taskCode           分支任务编码
 * @param traceId            分支 Trace ID
 * @param sourceTaskId       来源任务 ID
 * @param sourceCheckpointId 来源 Checkpoint ID
 * @param resumeStepNo       恢复步骤序号
 */
public record RuntimeCheckpointForkResult(
        Long taskId,
        String taskCode,
        String traceId,
        Long sourceTaskId,
        Long sourceCheckpointId,
        Integer resumeStepNo
) {
}
