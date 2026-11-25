package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunLifecycleEventServiceTest {

    @Test
    void shouldPersistWaitingStatusAndEventTogether() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(repository.markCompleted("RUN_1", "worker-1", 12L, "waiting_approval"))
                .thenReturn(true);
        AgentRunLifecycleEventService service = new AgentRunLifecycleEventService(repository, publisher);

        boolean updated = service.markCompleted("RUN_1", "worker-1", 12L, "waiting_approval");

        assertThat(updated).isTrue();
        verify(publisher).publishPayload(
                eq("RUN_1"),
                eq(RuntimeEventType.RUN_WAITING),
                eq(Map.of("status", "waiting_approval", "taskId", 12L)),
                eq(Map.of())
        );
    }

    @Test
    void shouldPublishCompletedAfterApprovalResume() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(repository.syncTaskStatus(12L, "success", null)).thenReturn(List.of(
                new AgentRunQueueRepository.RunStatusUpdate("RUN_1", 12L, "completed")));
        AgentRunLifecycleEventService service = new AgentRunLifecycleEventService(repository, publisher);

        boolean updated = service.syncTaskStatus(12L, "success", null);

        assertThat(updated).isTrue();
        verify(publisher).publishPayload(
                eq("RUN_1"), eq(RuntimeEventType.RUN_COMPLETED),
                eq(Map.of("status", "completed", "taskId", 12L)), eq(Map.of()));
    }

    @Test
    void shouldNotPublishDuplicateTerminalEventWithoutStatusChange() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(repository.syncTaskStatus(12L, "success", null)).thenReturn(List.of());
        AgentRunLifecycleEventService service = new AgentRunLifecycleEventService(repository, publisher);

        boolean updated = service.syncTaskStatus(12L, "success", null);

        assertThat(updated).isFalse();
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }
}
