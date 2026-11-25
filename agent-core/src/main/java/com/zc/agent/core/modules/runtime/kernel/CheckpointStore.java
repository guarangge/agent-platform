package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpointForkResult;

import java.util.Optional;

/** 可替换的运行时 Checkpoint 存储与恢复契约。 */
public interface CheckpointStore {

    /** 返回稳定存储实现编码。 */
    String storeCode();

    /** 保存当前步骤执行现场。 */
    void save(CheckpointSaveRequest request);

    /** 读取任务最新 Checkpoint。 */
    Optional<RuntimeCheckpoint> latest(Long taskId);

    /** 将异常中断现场准备为可恢复状态。 */
    RuntimeCheckpoint prepareAutomaticRecovery(Long taskId, Long operatorId);

    /** 标记 Checkpoint 已被恢复。 */
    void markRestored(RuntimeCheckpoint checkpoint, String recoveryMode);

    /** 处理外部副作用不确定的恢复处置。 */
    RuntimeCheckpoint resolveAmbiguity(ExecutionContext executionContext,
                                       Long taskId,
                                       ResolveCheckpointAmbiguityRequest request,
                                       Long operatorId);

    /** 从指定 Checkpoint 创建独立恢复分支。 */
    RuntimeCheckpointForkResult fork(Long sourceTaskId, Long checkpointId, Long operatorId);
}
