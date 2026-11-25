package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.AgentRunWorkerProperties;
import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import com.zc.agent.core.modules.conversation.model.ConversationRecord;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AgentRunServiceTest {

    private AgentRunQueueRepository runRepository;
    private ConversationRepository conversationRepository;
    private AgentRunWorker runWorker;
    private AgentRunService runService;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        runRepository = mock(AgentRunQueueRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        runWorker = mock(AgentRunWorker.class);
        runService = new AgentRunService(
                runRepository,
                conversationRepository,
                runWorker,
                new AgentRunWorkerProperties(),
                new ObjectMapper()
        );
        securityUser = new SecurityUser(1L, "tester", "Tester", List.of("ADMIN"), List.of("chat"));
    }

    @Test
    void shouldReturnOwnedRunStatus() {
        AgentRunRecord record = run("RUN_TEST", "running", false);
        when(runRepository.findByRunCode("RUN_TEST", 1L)).thenReturn(Optional.of(record));

        var response = runService.get("RUN_TEST", securityUser);

        assertThat(response.runCode()).isEqualTo("RUN_TEST");
        assertThat(response.status()).isEqualTo("running");
        assertThat(response.attemptCount()).isEqualTo(1);
    }

    @Test
    void shouldPersistCancelRequestAndInterruptActiveWorker() {
        AgentRunRecord running = run("RUN_CANCEL", "running", false);
        AgentRunRecord cancelling = run("RUN_CANCEL", "running", true);
        when(runRepository.findByRunCode("RUN_CANCEL", 1L))
                .thenReturn(Optional.of(running), Optional.of(cancelling));

        var response = runService.cancel("RUN_CANCEL", securityUser);

        verify(runRepository).requestCancel("RUN_CANCEL", 1L);
        verify(runWorker).interrupt("RUN_CANCEL");
        assertThat(response.cancelRequested()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"waiting_approval", "waiting_event", "paused"})
    void shouldNotSendIneffectiveCancelRequestForSuspendedRun(String status) {
        AgentRunRecord suspended = run("RUN_SUSPENDED", status, false);
        when(runRepository.findByRunCode("RUN_SUSPENDED", 1L)).thenReturn(Optional.of(suspended));

        var response = runService.cancel("RUN_SUSPENDED", securityUser);

        assertThat(response.status()).isEqualTo(status);
        verify(runRepository, never()).requestCancel(anyString(), anyLong());
        verify(runWorker, never()).interrupt(anyString());
    }

    @Test
    void shouldReturnExistingRunForIdempotencyKey() {
        ConversationRecord conversation = mock(ConversationRecord.class);
        when(conversation.agentId()).thenReturn(100L);
        when(runRepository.findByIdempotencyKey(10L, 1L, "request-1"))
                .thenReturn(Optional.of(run("RUN_EXISTING", "queued", false)));
        when(conversationRepository.findConversationById(10L, 1L)).thenReturn(Optional.of(conversation));

        var response = runService.enqueue(
                10L, new SendMessageRequest("hello"), "request-1", 80, securityUser
        );

        assertThat(response.runCode()).isEqualTo("RUN_EXISTING");
        verify(runRepository, never()).insert(anyString(), anyLong(), anyLong(), anyLong(), anyString(),
                anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldRejectWhenPersistentQueueReachesCapacity() {
        ConversationRecord conversation = mock(ConversationRecord.class);
        when(conversation.agentId()).thenReturn(100L);
        when(conversationRepository.findConversationById(10L, 1L)).thenReturn(Optional.of(conversation));
        when(runRepository.insert(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runService.enqueue(
                        10L, new SendMessageRequest("hello"), null, null, securityUser
                ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("容量上限");
    }

    private AgentRunRecord run(String runCode, String status, boolean cancelRequested) {
        return new AgentRunRecord(
                1L, runCode, 10L, 1L, 100L, "input", "{}", status,
                1, 2, 50, 20L, null, cancelRequested,
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(1), "worker-1",
                LocalDateTime.now(), LocalDateTime.now(), null
        );
    }
}
