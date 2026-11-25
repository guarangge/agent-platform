package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedArtifact;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.repository.RuntimeReplayRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayInstanceServiceTest {

    @Test
    void shouldFreezeSafePromptReplayManifestAndInput() throws Exception {
        RuntimeReplayRepository repository = mock(RuntimeReplayRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ReplayInstanceService service = new ReplayInstanceService(repository, objectMapper);
        ExecutionContext.ModelContext model = new ExecutionContext.ModelContext(
                "MODEL", "Model", "mock", "chat", "http://secret", "secret-key", "/chat", "mock-model",
                0.2D, 1024, 8192, 1024, 256, 0.8D, "cl100k", 30, "enabled", null
        );

        var replay = service.startPromptReplay(new ReplayInstanceService.StartPromptReplayCommand(
                20L, 100L, "TRACE_1", 30L, "Agent", "workflow_plan", model,
                "system", "user", "原始回答", List.of(Map.of("promptId", 1)),
                List.of(Map.of("toolCode", "DOC_SEARCH")),
                featureDecision(ReplayMode.PROMPT_REPLAY), 1L
        ));

        ArgumentCaptor<RuntimeReplayRepository.ReplayInsertCommand> captor =
                ArgumentCaptor.forClass(RuntimeReplayRepository.ReplayInsertCommand.class);
        verify(repository).insert(captor.capture());
        JsonNode manifest = objectMapper.readTree(captor.getValue().manifestJson());
        JsonNode input = objectMapper.readTree(captor.getValue().inputSnapshotJson());
        assertThat(replay.replayMode().code()).isEqualTo("prompt_replay");
        assertThat(manifest.path("policy").path("toolExecutionAllowed").asBoolean()).isFalse();
        assertThat(manifest.path("toolSchemas").isArray()).isTrue();
        assertThat(manifest.path("featureDecision").path("reason").asText()).isEqualTo("full_rollout");
        assertThat(manifest.toString()).doesNotContain("secret-key", "http://secret");
        assertThat(input.path("systemPrompt").asText()).isEqualTo("system");
        assertThat(input.path("userPromptSha256").asText()).hasSize(64);
    }

    @Test
    void shouldOnlyUpdateResultWhenReplayCompletes() {
        RuntimeReplayRepository repository = mock(RuntimeReplayRepository.class);
        when(repository.markSucceeded("REPLAY_1", "{\"ok\":true}", 1L)).thenReturn(1);
        ReplayInstanceService service = new ReplayInstanceService(repository, new ObjectMapper());
        var replay = new com.zc.agent.core.modules.runtime.replay.model.ReplayInstance(
                "REPLAY_1", "REPLAY_TRACE_1",
                com.zc.agent.core.modules.runtime.replay.model.ReplayMode.PROMPT_REPLAY, Map.of());

        service.complete(replay, Map.of("ok", true), 1L);

        verify(repository).markSucceeded("REPLAY_1", "{\"ok\":true}", 1L);
    }

    @Test
    void shouldFreezeSimulationReplayEventRangeAndDenySideEffects() throws Exception {
        RuntimeReplayRepository repository = mock(RuntimeReplayRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ReplayInstanceService service = new ReplayInstanceService(repository, objectMapper);
        RuntimeEventEnvelope event = new RuntimeEventEnvelope(
                "EVT_1", 1L, 10L, null, "RUN_1", 20L, "STEP_100", "step.completed",
                LocalDateTime.now(), 1, Map.of("taskStepId", 100L), Map.of()
        );
        RecordedStepResult result = new RecordedStepResult(
                100L, 1, "tool", "查询", "tool", Map.of("resultType", "tool"), "hash"
        );
        RecordedArtifact artifact = new RecordedArtifact(
                100L, "ARTIFACT_1", "report.pdf", "report.pdf", 5L, 5L,
                "application/pdf", "content-hash");

        var replay = service.startSimulationReplay(new ReplayInstanceService.StartSimulationReplayCommand(
                "RUN_1", 20L, "TRACE_1", 30L, "tool_only", 1L, 1L,
                List.of(event), List.of(result), List.of(artifact),
                featureDecision(ReplayMode.SIMULATION_REPLAY), 1L
        ));

        ArgumentCaptor<RuntimeReplayRepository.ReplayInsertCommand> captor =
                ArgumentCaptor.forClass(RuntimeReplayRepository.ReplayInsertCommand.class);
        verify(repository).insert(captor.capture());
        JsonNode manifest = objectMapper.readTree(captor.getValue().manifestJson());
        JsonNode input = objectMapper.readTree(captor.getValue().inputSnapshotJson());
        assertThat(replay.replayMode()).isEqualTo(com.zc.agent.core.modules.runtime.replay.model.ReplayMode.SIMULATION_REPLAY);
        assertThat(manifest.path("eventRange").path("toSequence").asLong()).isEqualTo(1L);
        assertThat(manifest.path("policy").path("toolExecutionAllowed").asBoolean()).isFalse();
        assertThat(manifest.path("policy").path("llmInvocationAllowed").asBoolean()).isFalse();
        assertThat(manifest.path("resourceSnapshot").path("providerInvocationAllowed").asBoolean()).isFalse();
        assertThat(input.path("events").size()).isEqualTo(1);
        assertThat(input.path("recordedStepResults").get(0).path("outputSha256").asText()).isEqualTo("hash");
        assertThat(input.path("recordedArtifacts").get(0).path("contentSha256").asText())
                .isEqualTo("content-hash");
        assertThat(manifest.path("featureDecision").path("replayMode").asText())
                .isEqualTo("simulation_replay");
    }

    private ReplayFeaturePolicy.Decision featureDecision(ReplayMode mode) {
        return new ReplayFeaturePolicy.Decision(true, mode.code(), "full_rollout", null);
    }
}
