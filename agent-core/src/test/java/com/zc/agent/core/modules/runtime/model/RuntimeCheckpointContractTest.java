package com.zc.agent.core.modules.runtime.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeCheckpointContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPreserveCheckpointSnapshotObjectsWithoutConversion() {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        ExecutionDecision decision = mock(ExecutionDecision.class);
        List<RuntimeStepResultContext> results = List.of(new RuntimeStepResultContext(
                1, "STEP_1", "tool", "查询", "完成", "结果", "{}"));

        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                1L, 10L, 20L, "step_succeeded", 1, 2,
                plan, decision, results, "TRACE_1", "safe_resume", null);

        assertThat(checkpoint.plan()).isSameAs(plan);
        assertThat(checkpoint.decision()).isSameAs(decision);
        assertThat(checkpoint.results()).isSameAs(results);
        assertThat(checkpoint.id()).isEqualTo(1L);
        assertThat(checkpoint.taskId()).isEqualTo(10L);
        assertThat(checkpoint.taskStepId()).isEqualTo(20L);
        assertThat(checkpoint.status()).isEqualTo("step_succeeded");
        assertThat(checkpoint.currentStepNo()).isEqualTo(1);
        assertThat(checkpoint.nextStepNo()).isEqualTo(2);
        assertThat(checkpoint.traceId()).isEqualTo("TRACE_1");
        assertThat(checkpoint.recoveryDisposition()).isEqualTo("safe_resume");
        assertThat(checkpoint.ambiguityReason()).isNull();
    }

    @Test
    void shouldPreserveLegacyCheckpointJsonContract() throws Exception {
        String legacyJson = """
                {
                  "id": 1,
                  "taskId": 10,
                  "taskStepId": 20,
                  "status": "step_failed",
                  "currentStepNo": 1,
                  "nextStepNo": 1,
                  "plan": null,
                  "decision": null,
                  "results": [],
                  "traceId": "TRACE_1",
                  "recoveryDisposition": "manual_review",
                  "ambiguityReason": "等待人工核验"
                }
                """;

        RuntimeCheckpoint checkpoint = objectMapper.readValue(legacyJson, RuntimeCheckpoint.class);
        String serialized = objectMapper.writeValueAsString(checkpoint);

        assertThat(objectMapper.readTree(serialized)).isEqualTo(objectMapper.readTree(legacyJson));
    }

    @Test
    void shouldPreserveLegacyForkResultJsonContract() throws Exception {
        String legacyJson = """
                {
                  "taskId": 40,
                  "taskCode": "TASK_FORK_1",
                  "traceId": "TRACE_FORK_1",
                  "sourceTaskId": 10,
                  "sourceCheckpointId": 1,
                  "resumeStepNo": 2
                }
                """;

        RuntimeCheckpointForkResult result = objectMapper.readValue(
                legacyJson, RuntimeCheckpointForkResult.class);
        String serialized = objectMapper.writeValueAsString(result);

        assertThat(objectMapper.readTree(serialized)).isEqualTo(objectMapper.readTree(legacyJson));
    }
}
