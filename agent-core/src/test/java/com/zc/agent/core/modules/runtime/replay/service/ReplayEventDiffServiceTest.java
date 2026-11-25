package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayEventDiffServiceTest {

    private final ReplayEventDiffService service = new ReplayEventDiffService(new ObjectMapper());

    @Test
    void shouldReportConsistentDiffForRecordedProjection() {
        List<RuntimeEventEnvelope> events = List.of(
                event(1L, "message.delta", Map.of("content", "一致结果")),
                event(2L, "step.completed", Map.of("taskStepId", 100L)),
                event(3L, "approval.resolved", Map.of("approvalId", 200L, "status", "approved"))
        );
        RecordedStepResult stepResult = stepResult();
        ReplayExecutionResult replayResult = new SimulationReplayModeHandler().execute(new ReplayExecutionInput(
                ReplayMode.SIMULATION_REPLAY, "RUN_1", 1L, 3L, events, List.of(stepResult)
        ));

        var diff = service.compare(events, List.of(stepResult), replayResult);

        assertThat(diff.consistent()).isTrue();
        assertThat(diff.summary()).isEqualTo(new com.zc.agent.core.modules.runtime.replay.model.ReplayEventDiff.Summary(
                2, 2, 2, 0, 0, 0));
        assertThat(diff.resultDiff().status()).isEqualTo("matched");
        assertThat(diff.entries()).allSatisfy(entry -> assertThat(entry.status()).isEqualTo("matched"));
        assertThat(diff.inconsistencyReasons()).isEmpty();
    }

    @Test
    void shouldReportChangedMissingUnexpectedAndResultMismatch() {
        List<RuntimeEventEnvelope> events = List.of(
                event(1L, "message.delta", Map.of("content", "原结果")),
                event(2L, "step.completed", Map.of("taskStepId", 100L)),
                event(3L, "approval.resolved", Map.of("approvalId", 200L, "status", "approved"))
        );
        RecordedStepResult stepResult = stepResult();
        ReplayExecutionResult replayResult = new ReplayExecutionResult(
                Map.of("assembledMessage", "新结果"),
                List.of(
                        Map.<String, Object>ofEntries(
                                Map.entry("sourceEventId", "EVT_2"),
                                Map.entry("sourceSequenceNo", 2L),
                                Map.entry("sourceEventType", "step.completed"),
                                Map.entry("observationType", "tool_result"),
                                Map.entry("source", "recorded_task_step_snapshot"),
                                Map.entry("taskStepId", 100L),
                                Map.entry("stepNo", 1),
                                Map.entry("stepType", "tool"),
                                Map.entry("stepName", "查询"),
                                Map.entry("outputSha256", "changed-hash"),
                                Map.entry("outputSnapshot", stepResult.outputSnapshot())
                        ),
                        Map.of(
                                "sourceEventId", "EVT_99", "sourceSequenceNo", 99L,
                                "sourceEventType", "artifact.published", "observationType", "artifact_result",
                                "source", "recorded_runtime_event", "payload", Map.of("artifactId", "EXTRA")
                        )
                )
        );

        var diff = service.compare(events, List.of(stepResult), replayResult);

        assertThat(diff.consistent()).isFalse();
        assertThat(diff.summary().changedCount()).isEqualTo(1);
        assertThat(diff.summary().missingCount()).isEqualTo(1);
        assertThat(diff.summary().unexpectedCount()).isEqualTo(1);
        assertThat(diff.resultDiff().status()).isEqualTo("changed");
        assertThat(diff.entries()).anySatisfy(entry -> {
            assertThat(entry.factKey()).isEqualTo("step:100");
            assertThat(entry.status()).isEqualTo("changed");
            assertThat(entry.changedFields()).containsExactly("outputSha256");
        });
        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry.status()).isEqualTo("missing"));
        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry.status()).isEqualTo("unexpected"));
        assertThat(diff.inconsistencyReasons()).hasSize(4);
    }

    @Test
    void shouldIgnoreJsonObjectFieldOrder() {
        RuntimeEventEnvelope event = event(1L, "step.completed", Map.of("taskStepId", 100L));
        RecordedStepResult expected = stepResult();
        LinkedHashMap<String, Object> reorderedSnapshot = new LinkedHashMap<>();
        reorderedSnapshot.put("responseText", "录制结果");
        reorderedSnapshot.put("resultType", "tool");
        Map<String, Object> actualObservation = Map.ofEntries(
                Map.entry("sourceEventId", "EVT_1"),
                Map.entry("sourceSequenceNo", 1L),
                Map.entry("sourceEventType", "step.completed"),
                Map.entry("observationType", "tool_result"),
                Map.entry("source", "recorded_task_step_snapshot"),
                Map.entry("taskStepId", 100L),
                Map.entry("stepNo", 1),
                Map.entry("stepType", "tool"),
                Map.entry("stepName", "查询"),
                Map.entry("outputSha256", "original-hash"),
                Map.entry("outputSnapshot", reorderedSnapshot)
        );

        var diff = service.compare(
                List.of(event), List.of(expected),
                new ReplayExecutionResult(Map.of("assembledMessage", ""), List.of(actualObservation))
        );

        assertThat(diff.consistent()).isTrue();
        assertThat(diff.entries()).singleElement().satisfies(entry ->
                assertThat(entry.status()).isEqualTo("matched"));
    }

    private RecordedStepResult stepResult() {
        return new RecordedStepResult(
                100L, 1, "tool", "查询", "tool",
                Map.of("resultType", "tool", "responseText", "录制结果"), "original-hash"
        );
    }

    private RuntimeEventEnvelope event(Long sequenceNo, String eventType, Map<String, Object> payload) {
        return new RuntimeEventEnvelope(
                "EVT_" + sequenceNo, sequenceNo, 10L, null, "RUN_1", 20L, null,
                eventType, LocalDateTime.now(), 1, payload, Map.of()
        );
    }
}
