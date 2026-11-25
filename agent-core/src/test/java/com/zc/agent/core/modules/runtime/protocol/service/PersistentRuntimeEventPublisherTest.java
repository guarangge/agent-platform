package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentRuntimeEventPublisherTest {

    @Test
    void shouldPersistCanonicalEventTypeAndPayloadVersion() {
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        when(repository.append(eq("RUN_1"), eq("run.progress"), eq("正在规划"), anyString(), eq(1)))
                .thenReturn(3L);
        PersistentRuntimeEventPublisher publisher = new PersistentRuntimeEventPublisher(
                repository, new ObjectMapper());

        long sequenceNo = publisher.publish(
                "RUN_1",
                RuntimeEventType.RUN_PROGRESS,
                "正在规划",
                Map.of("stage", "planning")
        );

        assertThat(sequenceNo).isEqualTo(3L);
        verify(repository).append(
                eq("RUN_1"),
                eq("run.progress"),
                eq("正在规划"),
                eq("{\"stage\":\"planning\"}"),
                eq(1)
        );
    }

    @Test
    void shouldSerializeStructuredLifecyclePayload() {
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        when(repository.append(eq("RUN_2"), eq("run.completed"), anyString(), eq("{}"), eq(1)))
                .thenReturn(4L);
        PersistentRuntimeEventPublisher publisher = new PersistentRuntimeEventPublisher(
                repository, new ObjectMapper());

        long sequenceNo = publisher.publishPayload(
                "RUN_2",
                RuntimeEventType.RUN_COMPLETED,
                Map.of("status", "completed", "taskId", 12L),
                Map.of()
        );

        assertThat(sequenceNo).isEqualTo(4L);
        verify(repository).append(
                eq("RUN_2"),
                eq("run.completed"),
                argThat(content -> content.contains("\"taskId\":12")
                        && content.contains("\"status\":\"completed\"")),
                eq("{}"),
                eq(1)
        );
    }
}
