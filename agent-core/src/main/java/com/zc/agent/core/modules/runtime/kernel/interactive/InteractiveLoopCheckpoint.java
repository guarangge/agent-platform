package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.util.List;
import java.util.Map;

/**
 * Interactive Loop 在审批边界的持久化恢复现场。
 *
 * @param id Checkpoint ID
 * @param taskId Task ID
 * @param traceId Trace ID
 * @param budgetState 完整预算计数和不可逆 Tool 指纹
 * @param observations 审批前已完成的有界 Observation
 * @param pendingAction 触发审批、尚未完成的 Tool Action
 * @param approvalNodeId 审批节点 ID
 * @param approvalNodeName 审批节点名称
 * @param toolCallId 审批绑定的 Tool Call ID
 * @param parameterHash 审批绑定的参数哈希
 * @param approvalMetadata 脱敏审批策略元数据
 */
public record InteractiveLoopCheckpoint(
        Long id,
        Long taskId,
        String traceId,
        InteractiveLoopBudgetState.PersistentSnapshot budgetState,
        List<InteractiveLoopObservation> observations,
        InteractiveLoopAction pendingAction,
        String approvalNodeId,
        String approvalNodeName,
        String toolCallId,
        String parameterHash,
        Map<String, Object> approvalMetadata
) {
    public InteractiveLoopCheckpoint {
        observations = observations == null ? List.of() : List.copyOf(observations);
        approvalMetadata = approvalMetadata == null ? Map.of() : Map.copyOf(approvalMetadata);
    }
}
