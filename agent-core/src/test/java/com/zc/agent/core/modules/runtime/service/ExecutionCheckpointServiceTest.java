package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.protocol.service.CheckpointRuntimeEventService;
import com.zc.agent.core.modules.tool.service.ToolSideEffectService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionCheckpointServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ToolSideEffectService toolSideEffectService = mock(ToolSideEffectService.class);
    private final CheckpointRuntimeEventService checkpointRuntimeEventService = mock(CheckpointRuntimeEventService.class);

    @Test
    void shouldCastAllCheckpointSnapshotsToJsonb() {
        ExecutionCheckpointService service = new ExecutionCheckpointService(
                jdbc, new ObjectMapper(), conversationRepository, toolSideEffectService,
                checkpointRuntimeEventService);
        ExecutionPlan plan = plan();
        ExecutionDecision decision = new ExecutionDecision("planning", "test", List.of(), List.of());
        RuntimeExecutionRequest request =
                new RuntimeExecutionRequest(
                        null, decision, plan, 8L, "TRACE_1", "{}", "Bearer test", 3L,
                        1, 0, List.of(), null, null, null);

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(21L);

        service.save(request, 10L, 1, 2, "step_started", List.of());

        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb)")),
                eq(Long.class), any(Object[].class));
        verify(checkpointRuntimeEventService).created(
                org.mockito.ArgumentMatchers.argThat(command -> command.checkpointId().equals(21L)
                        && command.taskId().equals(8L)
                        && command.checkpointStatus().equals("step_started")));
    }

    @Test
    void shouldPrepareNextStepAfterLastSuccessfulCheckpoint() {
        ExecutionCheckpointService service = spy(new ExecutionCheckpointService(
                jdbc, new ObjectMapper(), conversationRepository, toolSideEffectService,
                checkpointRuntimeEventService));
        ExecutionPlan plan = plan();
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                7L, 8L, 10L, "step_succeeded", 1, 2, plan,
                new ExecutionDecision("tool", "test", List.of(), List.of()), List.of(), "TRACE_1", "safe_resume", null);
        when(service.latest(8L)).thenReturn(Optional.of(checkpoint));
        when(toolSideEffectService.assessRecovery(8L)).thenReturn(
                new ToolSideEffectService.AmbiguityAssessment(false, "safe_resume", "", List.of()));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(conversationRepository.insertTaskStep(8L, 2, "reply", "回复", "{\"recovery\":\"durable_checkpoint\"}", 3L))
                .thenReturn(12L);

        RuntimeCheckpoint restored = service.prepareAutomaticRecovery(8L, 3L);

        assertThat(restored.nextStepNo()).isEqualTo(2);
        verify(conversationRepository).insertTaskStep(8L, 2, "reply", "回复",
                "{\"recovery\":\"durable_checkpoint\"}", 3L);
    }

    @Test
    void shouldBlockAutomaticRecoveryWhenSideEffectIsAmbiguous() {
        ExecutionCheckpointService service = spy(new ExecutionCheckpointService(
                jdbc, new ObjectMapper(), conversationRepository, toolSideEffectService,
                checkpointRuntimeEventService));
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                7L, 8L, 10L, "step_started", 1, 1, plan(),
                new ExecutionDecision("tool", "test", List.of(), List.of()), List.of(), "TRACE_1", "safe_resume", null);
        when(service.latest(8L)).thenReturn(Optional.of(checkpoint));
        when(toolSideEffectService.assessRecovery(8L)).thenReturn(
                new ToolSideEffectService.AmbiguityAssessment(true, "manual_review",
                        "存在不可逆 Tool，禁止自动重放", List.of(99L)));

        assertThatThrownBy(() -> service.prepareAutomaticRecovery(8L, 3L))
                .hasMessageContaining("禁止自动重放");
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldMarkCheckpointRestoredAndPublishEvent() {
        ExecutionCheckpointService service = new ExecutionCheckpointService(
                jdbc, new ObjectMapper(), conversationRepository, toolSideEffectService,
                checkpointRuntimeEventService);
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                7L, 8L, 10L, "step_succeeded", 1, 2, plan(),
                new ExecutionDecision("tool", "test", List.of(), List.of()), List.of(),
                "TRACE_1", "safe_resume", null);

        service.markRestored(checkpoint, "task_resume");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("restored_at=CURRENT_TIMESTAMP"), eq(7L));
        verify(checkpointRuntimeEventService).restored(
                org.mockito.ArgumentMatchers.argThat(command -> command.checkpointId().equals(7L)
                        && command.nextStepNo().equals(2)
                        && command.recoveryDisposition().equals("safe_resume")),
                eq("task_resume"));
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan("PLAN_1", "tool", "完成任务", "ready", List.of(), List.of(), List.of(), List.of(
                new ExecutionPlan.ExecutionPlanStep(1, "s1", "tool", "检索", "检索", List.of(), "SEARCH", null,
                        Map.of(), "结果", "fail", false, "ready", ""),
                new ExecutionPlan.ExecutionPlanStep(2, "s2", "reply", "回复", "回复", List.of("s1"), null, null,
                        Map.of(), "答案", "fail", false, "ready", "")
        ));
    }
}
