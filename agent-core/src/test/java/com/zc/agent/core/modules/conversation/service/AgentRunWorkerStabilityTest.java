package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.AgentRunWorkerProperties;
import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunWorkerStabilityTest {

    @Test
    void shouldClaimOnlyLocalAvailableCapacityInBatch() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        Executor holdingExecutor = command -> { };
        AgentRunWorker worker = worker(repository, holdingExecutor, properties(2, 10));
        when(repository.claimBatch(anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(run(1L), run(2L)));

        worker.poll();
        worker.poll();

        verify(repository).claimBatch(anyString(), anyInt(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(10), anyInt());
    }

    @Test
    void shouldReleaseClaimWhenExecutorRejectsSubmission() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        Executor rejectingExecutor = command -> { throw new java.util.concurrent.RejectedExecutionException("full"); };
        AgentRunWorker worker = worker(repository, rejectingExecutor, properties(1, 4));
        AgentRunRecord run = run(1L);
        when(repository.claimBatch(anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(run));

        worker.poll();

        verify(repository).releaseClaim(org.mockito.ArgumentMatchers.eq(run.runCode()), anyString());
        verify(repository, never()).markCompleted(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldUseCappedExponentialBackoffWithStableJitter() {
        AgentRunQueueRepository repository = mock(AgentRunQueueRepository.class);
        AgentRunWorkerProperties properties = properties(1, 4);
        properties.setRetryInitialSeconds(2);
        properties.setRetryMaxSeconds(5);
        AgentRunWorker worker = worker(repository, Runnable::run, properties);

        int first = worker.calculateBackoffSeconds(run(1L, 1));
        int fourth = worker.calculateBackoffSeconds(run(1L, 4));

        assertThat(first).isBetween(2, 3);
        assertThat(fourth).isBetween(5, 6);
    }

    private AgentRunWorker worker(AgentRunQueueRepository repository,
                                  Executor executor,
                                  AgentRunWorkerProperties properties) {
        return new AgentRunWorker(
                repository,
                mock(ConversationRepository.class),
                mock(ConversationService.class),
                properties,
                new ObjectMapper(),
                executor,
                ObservationRegistry.NOOP,
                mock(Tracer.class)
        );
    }

    private AgentRunWorkerProperties properties(int concurrency, int globalConcurrency) {
        AgentRunWorkerProperties properties = new AgentRunWorkerProperties();
        properties.setConcurrency(concurrency);
        properties.setClaimBatchSize(concurrency);
        properties.setGlobalConcurrency(globalConcurrency);
        return properties;
    }

    private AgentRunRecord run(long id) {
        return run(id, 1);
    }

    private AgentRunRecord run(long id, int attempts) {
        LocalDateTime now = LocalDateTime.now();
        return new AgentRunRecord(
                id, "RUN_" + id, 10L, id, 100L, "input", "{}", "running",
                attempts, 5, 50, null, null, false,
                now, now.plusMinutes(1), "worker", now, now, null
        );
    }
}
