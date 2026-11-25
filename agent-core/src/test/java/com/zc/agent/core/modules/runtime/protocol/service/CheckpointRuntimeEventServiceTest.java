package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckpointRuntimeEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishCreatedAndRestoredWithoutSnapshotContent() {
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(runRepository.findRunCodeByTaskId(12L)).thenReturn(Optional.of("RUN_1"));
        CheckpointRuntimeEventService service = new CheckpointRuntimeEventService(runRepository, publisher);
        var command = new CheckpointRuntimeEventService.CheckpointEventCommand(
                71L, 12L, 31L, "step_succeeded", 1, 2,
                "PLAN_1", "TRACE_1", "safe_resume");

        service.created(command);
        AgentRunStreamingContext.bind("RUN_1");
        service.restored(command, "task_resume");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> createdPayload = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> restoredPayload = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.CHECKPOINT_CREATED),
                createdPayload.capture(), eq(Map.of("traceId", "TRACE_1")));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.CHECKPOINT_RESTORED),
                restoredPayload.capture(), eq(Map.of("traceId", "TRACE_1")));

        assertThat(createdPayload.getValue())
                .containsEntry("checkpointId", 71L)
                .containsEntry("checkpointStatus", "step_succeeded")
                .containsEntry("status", "created")
                .doesNotContainKeys("planSnapshot", "decisionSnapshot", "stepResultsSnapshot");
        assertThat(restoredPayload.getValue())
                .containsEntry("checkpointId", 71L)
                .containsEntry("recoveryMode", "task_resume")
                .containsEntry("recoveryDisposition", "safe_resume")
                .containsEntry("status", "restored")
                .doesNotContainKeys("planSnapshot", "decisionSnapshot", "stepResultsSnapshot");
    }
}
