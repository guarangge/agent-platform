package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunStreamServiceTest {

    @Test
    void shouldStreamPersistedAssistantMessageAfterRunCompleted() throws Exception {
        AgentRunService agentRunService = mock(AgentRunService.class);
        ConversationRepository repository = mock(ConversationRepository.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        AgentRunResponse run = new AgentRunResponse(
                "RUN_1", 3L, 2L, "completed", 1, 3, 0, 9L,
                null, false, null, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), "default", "[]");
        MessageRecord assistantMessage = new MessageRecord(
                12L, 3L, "assistant", 2, "这是已通过输出校验并落库的回答。",
                "success", null, LocalDateTime.now());
        when(agentRunService.get("RUN_1", securityUser)).thenReturn(run);
        when(repository.findAssistantMessageByTaskId(9L)).thenReturn(Optional.of(assistantMessage));
        when(streamEventService.findAfter("RUN_1", 0L)).thenReturn(java.util.List.of());
        AgentRunStreamService service = new AgentRunStreamService(
                agentRunService, repository, streamEventService, new ObjectMapper());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.stream(3L, "RUN_1", securityUser).writeTo(output);

        String events = output.toString(StandardCharsets.UTF_8);
        assertThat(events).contains(
                "retry: 2000",
                "event: status",
                "\"status\":\"completed\"",
                "event: delta",
                "这是已通过输出校验并落库的回答。",
                "event: completed"
        );
    }

    @Test
    void shouldForwardNativeDeltasAndReplaceWithGuardedFinalMessage() throws Exception {
        AgentRunService agentRunService = mock(AgentRunService.class);
        ConversationRepository repository = mock(ConversationRepository.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        AgentRunResponse run = new AgentRunResponse(
                "RUN_NATIVE", 3L, 2L, "completed", 1, 3, 0, 9L,
                null, false, null, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), "default", "[]");
        when(agentRunService.get("RUN_NATIVE", securityUser)).thenReturn(run);
        when(streamEventService.findAfter("RUN_NATIVE", 0L)).thenReturn(List.of(
                new AgentRunStreamEventRecord(1L, "RUN_NATIVE", 1L, "run.progress", "正在生成执行计划",
                        "{\"stage\":\"planning\",\"stepCount\":2}", LocalDateTime.now()),
                new AgentRunStreamEventRecord(2L, "RUN_NATIVE", 2L, "message.reset", "", "{}", LocalDateTime.now()),
                new AgentRunStreamEventRecord(3L, "RUN_NATIVE", 3L, "message.delta", "模型原始输出", "{}", LocalDateTime.now())
        ));
        when(streamEventService.findAfter("RUN_NATIVE", 3L)).thenReturn(List.of());
        when(repository.findAssistantMessageByTaskId(9L)).thenReturn(Optional.of(new MessageRecord(
                12L, 3L, "assistant", 2, "策略校准后的输出", "success", null, LocalDateTime.now())));
        AgentRunStreamService service = new AgentRunStreamService(
                agentRunService, repository, streamEventService, new ObjectMapper());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.stream(3L, "RUN_NATIVE", securityUser).writeTo(output);

        String events = output.toString(StandardCharsets.UTF_8);
        assertThat(events).contains(
                "event: progress",
                "正在生成执行计划",
                "\"stage\":\"planning\"",
                "event: reset",
                "event: delta",
                "模型原始输出",
                "event: replace",
                "策略校准后的输出",
                "event: completed"
        );
    }
}
