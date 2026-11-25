package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageInputRuntimeEventServiceTest {

    @AfterEach
    void clearScope() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishReferencesAndHashWithoutBinaryContent() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.messageContext()).thenReturn(new ExecutionContext.MessageContext(1L, "问题", List.of(
                new ExecutionContext.MessageAttachmentContext(7L, "ATT_7", "图片.png", "image/png", 120L, "abc123"))));
        when(context.taskContext()).thenReturn(new ExecutionContext.TaskContext(2L, "TASK_2", "TRACE_2"));
        AgentRunStreamingContext.bind("RUN_1");

        new ImageInputRuntimeEventService(publisher).resolved(context, "native_vision", "vision-model");

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.INPUT_ATTACHMENT_RESOLVED),
                argThat(payload -> payload.toString().contains("abc123") && !payload.toString().contains("base64")),
                eq(Map.of("traceId", "TRACE_2")));
    }
}
