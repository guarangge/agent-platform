package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskStepLifecycleEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishStartedAndCompletedStepEvents() {
        ConversationRepository repository = mock(ConversationRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ArtifactRuntimeEventService artifactRuntimeEventService = mock(ArtifactRuntimeEventService.class);
        when(repository.insertTaskStep(12L, 1, "tool", "查询时间", "{}", 1L)).thenReturn(31L);
        TaskStepLifecycleEventService service = new TaskStepLifecycleEventService(
                repository, publisher, artifactRuntimeEventService);
        AgentRunStreamingContext.bind("RUN_1");
        var start = new TaskStepLifecycleEventService.StartStepCommand(
                12L, 1, "step-1", 1, 1, "tool", "查询时间", "{}", 1L);

        Long taskStepId = service.start(start);
        service.transition(new TaskStepLifecycleEventService.TransitionStepCommand(
                12L, taskStepId, "step-1", 1, 1, "tool", "查询时间", "success",
                "{\"result\":\"ok\"}", null, null, false, 1L));

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.STEP_STARTED),
                org.mockito.ArgumentMatchers.argThat(
                        payload -> payload.get("taskStepId").equals(31L)
                        && payload.get("status").equals("processing")), eq(Map.of()));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.STEP_COMPLETED),
                org.mockito.ArgumentMatchers.argThat(
                        payload -> payload.get("taskStepId").equals(31L)
                        && payload.get("status").equals("success")), eq(Map.of()));
        verify(artifactRuntimeEventService).publishCreated(12L, 31L, "{\"result\":\"ok\"}");
    }
}
