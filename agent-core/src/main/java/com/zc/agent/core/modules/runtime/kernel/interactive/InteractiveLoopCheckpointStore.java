package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;

import java.util.List;
import java.util.Optional;

/** Interactive Loop 审批挂起现场的持久化存储契约。 */
public interface InteractiveLoopCheckpointStore {

    /** 在向上抛出审批信号前保存可恢复现场。 */
    void saveWaitingApproval(Long taskId,
                             String traceId,
                             InteractiveLoopBudgetState.PersistentSnapshot budgetState,
                             List<InteractiveLoopObservation> observations,
                             InteractiveLoopAction pendingAction,
                             ApprovalRequiredException approval);

    /** 查询任务最新的待审批 Interactive Loop Checkpoint。 */
    Optional<InteractiveLoopCheckpoint> latestWaitingApproval(Long taskId);

    /** 待审批 Tool 已实际恢复执行后关闭 Checkpoint。 */
    void markRestored(Long checkpointId);
}
