package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpointForkResult;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.service.ExecutionCheckpointService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCheckpointStoreAdapterTest {

    @Test
    void shouldDelegateCheckpointSaveWithoutChangingSnapshotInputs() {
        ExecutionCheckpointService service = mock(ExecutionCheckpointService.class);
        LegacyCheckpointStoreAdapter adapter = new LegacyCheckpointStoreAdapter(service);
        RuntimeExecutionRequest executionRequest =
                new RuntimeExecutionRequest(
                        null, null, null, 10L, "TRACE_1", "{}", "Bearer token", 20L,
                        1, 0, List.of(), null, null, null);
        List<RuntimeStepResultContext> results = List.of(
                new RuntimeStepResultContext(
                        1, "STEP_1", "tool", "查询", "完成", "result", "{}")
        );
        CheckpointSaveRequest request = new CheckpointSaveRequest(
                executionRequest, 30L, 1, 2, "step_succeeded", results);

        adapter.save(request);

        assertThat(adapter.storeCode()).isEqualTo(LegacyCheckpointStoreAdapter.STORE_CODE);
        verify(service).save(executionRequest, 30L, 1, 2, "step_succeeded", results);
    }

    @Test
    void shouldDelegateRecoveryAndForkLifecycleWithoutMappingResults() {
        ExecutionCheckpointService service = mock(ExecutionCheckpointService.class);
        LegacyCheckpointStoreAdapter adapter = new LegacyCheckpointStoreAdapter(service);
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                1L, 10L, 20L, "step_failed", 1, 1,
                null, null, List.of(), "TRACE_1", null, null);
        RuntimeCheckpointForkResult forkResult = new RuntimeCheckpointForkResult(
                40L, "TASK_FORK_1", "TRACE_FORK_1", 10L, 1L, 1);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ResolveCheckpointAmbiguityRequest ambiguityRequest = mock(ResolveCheckpointAmbiguityRequest.class);
        when(service.latest(10L)).thenReturn(Optional.of(checkpoint));
        when(service.prepareAutomaticRecovery(10L, 50L)).thenReturn(checkpoint);
        when(service.resolveAmbiguity(executionContext, 10L, ambiguityRequest, 50L)).thenReturn(checkpoint);
        when(service.fork(10L, 1L, 50L)).thenReturn(forkResult);

        assertThat(adapter.latest(10L)).containsSame(checkpoint);
        assertThat(adapter.prepareAutomaticRecovery(10L, 50L)).isSameAs(checkpoint);
        adapter.markRestored(checkpoint, "task_resume");
        assertThat(adapter.resolveAmbiguity(executionContext, 10L, ambiguityRequest, 50L))
                .isSameAs(checkpoint);
        assertThat(adapter.fork(10L, 1L, 50L)).isSameAs(forkResult);

        verify(service).latest(10L);
        verify(service).prepareAutomaticRecovery(10L, 50L);
        verify(service).markRestored(checkpoint, "task_resume");
        verify(service).resolveAmbiguity(executionContext, 10L, ambiguityRequest, 50L);
        verify(service).fork(10L, 1L, 50L);
    }
}
