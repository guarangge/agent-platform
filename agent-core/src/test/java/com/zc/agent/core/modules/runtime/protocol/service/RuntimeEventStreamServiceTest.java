package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeEventStreamServiceTest {

    @Test
    void shouldStreamCanonicalEnvelopeWithSequenceId() throws Exception {
        RuntimeEventQueryService queryService = mock(RuntimeEventQueryService.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 31, 10, 0);
        AgentRunResponse completedRun = new AgentRunResponse(
                "RUN_1", 8L, 2L, "completed", 1, 3, 0, 11L,
                null, false, null, occurredAt, occurredAt, occurredAt, "default", "[]"
        );
        RuntimeEventEnvelope envelope = new RuntimeEventEnvelope(
                "EVT_21", 1L, 8L, null, "RUN_1", 11L, null,
                "message.delta", occurredAt, 1,
                Map.of("content", "统一事件正文"), Map.of()
        );
        when(queryService.findAfter("RUN_1", 0L, 1, securityUser))
                .thenReturn(new RuntimeEventQueryService.RuntimeEventBatch(completedRun, List.of()));
        when(queryService.findAfter("RUN_1", 0L, 200, securityUser))
                .thenReturn(new RuntimeEventQueryService.RuntimeEventBatch(completedRun, List.of(envelope)));
        RuntimeEventStreamService service = new RuntimeEventStreamService(
                queryService, new ObjectMapper().findAndRegisterModules());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.stream("RUN_1", 0L, securityUser).writeTo(output);

        String events = output.toString(StandardCharsets.UTF_8);
        assertThat(events).contains(
                "retry: 2000",
                "id: 1",
                "event: runtime.event",
                "\"eventId\":\"EVT_21\"",
                "\"eventType\":\"message.delta\"",
                "统一事件正文"
        );
    }
}
