package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationReplayModeHandlerTest {

    private final SimulationReplayModeHandler handler = new SimulationReplayModeHandler();

    @Test
    void shouldInjectRecordedToolResultWithoutProviderOrWrites() {
        List<RuntimeEventEnvelope> events = List.of(
                event(1L, "message.reset", Map.of()),
                event(2L, "message.delta", Map.of("content", "录制结果")),
                event(3L, "tool.call.completed", Map.of("toolCallId", "TOOL_CALL_1", "toolCode", "SEARCH")),
                event(4L, "step.completed", Map.of("taskStepId", 100L)),
                event(5L, "step.completed", Map.of("taskStepId", 101L)),
                event(6L, "approval.resolved", Map.of("approvalId", 200L, "status", "approved")),
                event(7L, "artifact.published", Map.of("artifactId", "ARTIFACT_1")),
                event(8L, "input.attachment.resolved", Map.of("route", "native_vision", "sha256", "abc123")),
                event(9L, "run.completed", Map.of("status", "completed"))
        );
        RecordedStepResult recordedResult = new RecordedStepResult(
                100L, 1, "tool", "搜索", "tool",
                Map.of("resultType", "tool", "responseText", "来自录制快照"), "sha256"
        );
        RecordedStepResult workflowResult = new RecordedStepResult(
                101L, 2, "workflow", "生成报告", "workflow",
                Map.of("resultType", "workflow", "responseText", "来自录制 Workflow"), "workflow-sha256"
        );

        var result = handler.execute(new ReplayExecutionInput(
                ReplayMode.SIMULATION_REPLAY, "RUN_1", 1L, 9L, events,
                List.of(recordedResult, workflowResult)
        ));

        assertThat(result.output()).containsEntry("executionMode", "recorded_result_injection");
        assertThat(result.output()).containsEntry("assembledMessage", "录制结果");
        assertThat(result.output()).containsEntry("providerInvocationCount", 0);
        assertThat(result.output()).containsEntry("llmInvocationCount", 0);
        assertThat(result.output()).containsEntry("networkWriteCount", 0);
        assertThat(result.output()).containsEntry("fileWriteCount", 0);
        assertThat(result.output()).containsEntry("databaseBusinessWriteCount", 0);
        assertThat(result.injectedObservations()).anySatisfy(observation -> {
            assertThat(observation).containsEntry("observationType", "tool_result");
            assertThat(observation).containsEntry("source", "recorded_task_step_snapshot");
            assertThat(observation).containsEntry("outputSha256", "sha256");
        });
        assertThat(result.injectedObservations()).anySatisfy(observation ->
                assertThat(observation).containsEntry("observationType", "workflow_result")
                        .containsEntry("outputSha256", "workflow-sha256"));
        assertThat(result.injectedObservations()).anySatisfy(observation ->
                assertThat(observation).containsEntry("observationType", "approval_result"));
        assertThat(result.injectedObservations()).anySatisfy(observation ->
                assertThat(observation).containsEntry("observationType", "artifact_result"));
        assertThat(result.injectedObservations()).anySatisfy(observation ->
                assertThat(observation).containsEntry("observationType", "attachment_input_fact"));
    }

    @Test
    void shouldFailWhenCompletedStepHasNoRecordedResult() {
        ReplayExecutionInput input = new ReplayExecutionInput(
                ReplayMode.SIMULATION_REPLAY, "RUN_1", 1L, 1L,
                List.of(event(1L, "step.completed", Map.of("taskStepId", 100L))), List.of()
        );

        assertThatThrownBy(() -> handler.execute(input))
                .hasMessage("Simulation Replay 缺少步骤结果快照: taskStepId=100");
    }

    private RuntimeEventEnvelope event(Long sequenceNo, String eventType, Map<String, Object> payload) {
        return new RuntimeEventEnvelope(
                "EVT_" + sequenceNo, sequenceNo, 10L, null, "RUN_1", 20L, null,
                eventType, LocalDateTime.now(), 1, payload, Map.of()
        );
    }
}
