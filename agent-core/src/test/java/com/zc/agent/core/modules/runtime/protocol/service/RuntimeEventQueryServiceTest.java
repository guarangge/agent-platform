package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeEventQueryServiceTest {

    @Test
    void shouldMapLegacyStoredEventToCanonicalEnvelope() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 31, 10, 0);
        AgentRunResponse run = new AgentRunResponse(
                "RUN_1", 8L, 2L, "running", 1, 3, 0, 11L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_1", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_1", 0L, 200)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        21L, "RUN_1", 1L, "progress", "正在构建上下文",
                        "{\"stage\":\"context\"}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_1", 0L, 200, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("EVT_21");
            assertThat(event.sequenceNo()).isEqualTo(1L);
            assertThat(event.threadId()).isEqualTo(8L);
            assertThat(event.runId()).isEqualTo("RUN_1");
            assertThat(event.taskId()).isEqualTo(11L);
            assertThat(event.eventType()).isEqualTo("run.progress");
            assertThat(event.payload()).containsEntry("message", "正在构建上下文");
            assertThat(event.metadata())
                    .containsEntry("stage", "context")
                    .containsEntry("legacyEventType", "progress");
        });
    }

    @Test
    void shouldPassThroughFutureCanonicalEventTypes() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        AgentRunResponse run = new AgentRunResponse(
                "RUN_2", 9L, 3L, "running", 1, 3, 0, 12L,
                null, false, null, LocalDateTime.now(), null, null, "default", "[]"
        );
        when(agentRunService.get("RUN_2", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_2", 5L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        22L, "RUN_2", 6L, "replay.started", "",
                        "{\"replayId\":\"REPLAY_1\"}", 1, LocalDateTime.now()
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_2", 5L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("replay.started");
            assertThat(event.metadata()).containsEntry("replayId", "REPLAY_1");
            assertThat(event.payload()).isEmpty();
        });
    }

    @Test
    void shouldExposeStructuredStepPayloadAndItemId() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_3", 10L, 4L, "running", 1, 3, 0, 13L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_3", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_3", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        23L, "RUN_3", 1L, "step.started",
                        "{\"taskStepId\":31,\"stepNo\":1,\"status\":\"processing\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_3", 0L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("step.started");
            assertThat(event.itemId()).isEqualTo("STEP_31");
            assertThat(event.payload())
                    .containsEntry("taskStepId", 31)
                    .containsEntry("status", "processing");
        });
    }

    @Test
    void shouldExposeToolCallIdAsItemIdForToolAndPolicyEvents() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_4", 11L, 5L, "running", 1, 3, 0, 14L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_4", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_4", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        24L, "RUN_4", 1L, "tool.call.started",
                        "{\"toolCallId\":\"TOOL_CALL_1\",\"toolCode\":\"WEATHER\",\"status\":\"running\"}",
                        "{}", 1, occurredAt
                ),
                new AgentRunStreamEventRecord(
                        25L, "RUN_4", 2L, "policy.decided",
                        "{\"toolCallId\":\"TOOL_CALL_1\",\"toolCode\":\"WEATHER\",\"decision\":\"approval\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_4", 0L, 10, securityUser);

        assertThat(batch.events()).hasSize(2).allSatisfy(event -> {
            assertThat(event.itemId()).isEqualTo("TOOL_CALL_1");
            assertThat(event.payload()).containsEntry("toolCode", "WEATHER");
        });
    }

    @Test
    void shouldExposeApprovalIdAsItemId() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_5", 12L, 6L, "waiting_approval", 1, 3, 0, 15L,
                null, false, null, occurredAt, occurredAt, occurredAt, "default", "[]"
        );
        when(agentRunService.get("RUN_5", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_5", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        25L, "RUN_5", 1L, "approval.requested",
                        "{\"approvalId\":55,\"taskStepId\":31,\"status\":\"pending\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_5", 0L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.itemId()).isEqualTo("APPROVAL_55");
            assertThat(event.payload())
                    .containsEntry("approvalId", 55)
                    .containsEntry("status", "pending");
        });
    }

    @Test
    void shouldExposeArtifactIdAsItemId() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_6", 13L, 7L, "running", 1, 3, 0, 16L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_6", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_6", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        26L, "RUN_6", 1L, "artifact.published",
                        "{\"artifactId\":\"ARTIFACT_A1\",\"fileName\":\"计划.pdf\",\"status\":\"published\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_6", 0L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.itemId()).isEqualTo("ARTIFACT_A1");
            assertThat(event.payload())
                    .containsEntry("fileName", "计划.pdf")
                    .containsEntry("status", "published");
        });
    }

    @Test
    void shouldExposeCheckpointIdAsItemId() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_7", 14L, 8L, "running", 1, 3, 0, 17L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_7", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_7", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        27L, "RUN_7", 1L, "checkpoint.restored",
                        "{\"checkpointId\":71,\"checkpointStatus\":\"step_succeeded\",\"status\":\"restored\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_7", 0L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.itemId()).isEqualTo("CHECKPOINT_71");
            assertThat(event.payload())
                    .containsEntry("checkpointStatus", "step_succeeded")
                    .containsEntry("status", "restored");
        });
    }

    @Test
    void shouldExposePlanIdAsItemId() {
        AgentRunService agentRunService = mock(AgentRunService.class);
        AgentRunStreamEventRepository repository = mock(AgentRunStreamEventRepository.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        AgentRunResponse run = new AgentRunResponse(
                "RUN_8", 15L, 9L, "running", 1, 3, 0, 18L,
                null, false, null, occurredAt, occurredAt, null, "default", "[]"
        );
        when(agentRunService.get("RUN_8", securityUser)).thenReturn(run);
        when(repository.findAfter("RUN_8", 0L, 10)).thenReturn(List.of(
                new AgentRunStreamEventRecord(
                        28L, "RUN_8", 1L, "plan.replanned",
                        "{\"planId\":\"PLAN_2\",\"planVersion\":2,\"status\":\"replanned\"}",
                        "{}", 1, occurredAt
                )
        ));
        RuntimeEventQueryService service = new RuntimeEventQueryService(
                agentRunService, repository, new ObjectMapper());

        RuntimeEventQueryService.RuntimeEventBatch batch = service.findAfter(
                "RUN_8", 0L, 10, securityUser);

        assertThat(batch.events()).singleElement().satisfies(event -> {
            assertThat(event.itemId()).isEqualTo("PLAN_2");
            assertThat(event.payload())
                    .containsEntry("planVersion", 2)
                    .containsEntry("status", "replanned");
        });
    }
}
