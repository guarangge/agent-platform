package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpointForkResult;
import com.zc.agent.core.modules.runtime.service.ExecutionCheckpointService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 将现有 PostgreSQL Checkpoint 服务适配为 CheckpointStore。 */
@Service
public class LegacyCheckpointStoreAdapter implements CheckpointStore {

    public static final String STORE_CODE = "postgres_checkpoint_store_v1";

    private final ExecutionCheckpointService executionCheckpointService;

    public LegacyCheckpointStoreAdapter(ExecutionCheckpointService executionCheckpointService) {
        this.executionCheckpointService = executionCheckpointService;
    }

    @Override
    public String storeCode() {
        return STORE_CODE;
    }

    @Override
    public void save(CheckpointSaveRequest request) {
        executionCheckpointService.save(
                request.executionRequest(),
                request.taskStepId(),
                request.currentStepNo(),
                request.nextStepNo(),
                request.status(),
                request.stepResults()
        );
    }

    @Override
    public Optional<RuntimeCheckpoint> latest(Long taskId) {
        return executionCheckpointService.latest(taskId);
    }

    @Override
    public RuntimeCheckpoint prepareAutomaticRecovery(Long taskId, Long operatorId) {
        return executionCheckpointService.prepareAutomaticRecovery(taskId, operatorId);
    }

    @Override
    public void markRestored(RuntimeCheckpoint checkpoint, String recoveryMode) {
        executionCheckpointService.markRestored(checkpoint, recoveryMode);
    }

    @Override
    public RuntimeCheckpoint resolveAmbiguity(ExecutionContext executionContext,
                                              Long taskId,
                                              ResolveCheckpointAmbiguityRequest request,
                                              Long operatorId) {
        return executionCheckpointService.resolveAmbiguity(executionContext, taskId, request, operatorId);
    }

    @Override
    public RuntimeCheckpointForkResult fork(Long sourceTaskId, Long checkpointId, Long operatorId) {
        return executionCheckpointService.fork(sourceTaskId, checkpointId, operatorId);
    }
}
