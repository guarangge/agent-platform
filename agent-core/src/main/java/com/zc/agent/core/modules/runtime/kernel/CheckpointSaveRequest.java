package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;

import java.util.List;

/**
 * Checkpoint 保存请求。
 *
 * @param executionRequest 当前 Orchestrator 执行请求
 * @param taskStepId 当前任务步骤 ID
 * @param currentStepNo 当前步骤序号
 * @param nextStepNo 下一步序号
 * @param status Checkpoint 状态
 * @param stepResults 已完成步骤结果
 */
public record CheckpointSaveRequest(
        RuntimeExecutionRequest executionRequest,
        Long taskStepId,
        Integer currentStepNo,
        Integer nextStepNo,
        String status,
        List<RuntimeStepResultContext> stepResults
) {
    public CheckpointSaveRequest {
        if (executionRequest == null) {
            throw new IllegalArgumentException("CheckpointStore executionRequest 不能为空");
        }
    }
}
