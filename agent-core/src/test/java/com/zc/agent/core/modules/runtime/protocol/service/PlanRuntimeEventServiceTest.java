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

class PlanRuntimeEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishCreatedAndReplannedWithoutPlanSnapshot() {
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(runRepository.findRunCodeByTaskId(12L)).thenReturn(Optional.of("RUN_1"));
        PlanRuntimeEventService service = new PlanRuntimeEventService(runRepository, publisher);
        var created = new PlanRuntimeEventService.PlanEventCommand(
                61L, "PLAN_1", 12L, 1, "agentic", "ready", 2,
                "initial", 92, null, null, Map.of(), "TRACE_1");
        var replanned = new PlanRuntimeEventService.PlanEventCommand(
                62L, "PLAN_2", 12L, 2, "agentic", "ready", 3,
                "auto_replan", 88, "PLAN_1", 1,
                Map.of("addedCount", 2, "removedCount", 1, "changedCount", 1), "TRACE_1");

        service.created(created);
        AgentRunStreamingContext.bind("RUN_1");
        service.replanned(replanned);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> createdPayload = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> replannedPayload = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.PLAN_CREATED),
                createdPayload.capture(), eq(Map.of("traceId", "TRACE_1")));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.PLAN_REPLANNED),
                replannedPayload.capture(), eq(Map.of("traceId", "TRACE_1")));

        assertThat(createdPayload.getValue())
                .containsEntry("planId", "PLAN_1")
                .containsEntry("planVersion", 1)
                .containsEntry("qualityScore", 92)
                .containsEntry("status", "created")
                .doesNotContainKeys("steps", "goal", "assumptions", "constraints", "risks");
        assertThat(replannedPayload.getValue())
                .containsEntry("planId", "PLAN_2")
                .containsEntry("previousPlanId", "PLAN_1")
                .containsEntry("previousPlanVersion", 1)
                .containsEntry("source", "auto_replan")
                .containsEntry("status", "replanned")
                .doesNotContainKeys("steps", "goal", "assumptions", "constraints", "risks");
        assertThat(replannedPayload.getValue().get("changeSummary")).isEqualTo(
                Map.of("addedCount", 2, "removedCount", 1, "changedCount", 1));
    }
}
