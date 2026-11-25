package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventQueryService;
import com.zc.agent.core.modules.runtime.replay.dto.SimulationReplayRequest;
import com.zc.agent.core.modules.runtime.replay.model.ArtifactVerificationReport;
import com.zc.agent.core.modules.runtime.replay.model.ReplayEventDiff;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayInstance;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SimulationReplayServiceTest {

    private AgentRunService agentRunService;
    private AgentRunStreamEventRepository eventRepository;
    private RuntimeEventQueryService eventQueryService;
    private ConversationRepository conversationRepository;
    private ReplayInstanceService replayInstanceService;
    private ReplayEngine replayEngine;
    private ReplayEventDiffService replayEventDiffService;
    private ArtifactReplayVerificationService artifactReplayVerificationService;
    private ReplayFeaturePolicy replayFeaturePolicy;
    private SimulationReplayService service;
    private SecurityUser administrator;

    @BeforeEach
    void setUp() {
        agentRunService = mock(AgentRunService.class);
        eventRepository = mock(AgentRunStreamEventRepository.class);
        eventQueryService = mock(RuntimeEventQueryService.class);
        conversationRepository = mock(ConversationRepository.class);
        replayInstanceService = mock(ReplayInstanceService.class);
        replayEngine = mock(ReplayEngine.class);
        replayEventDiffService = mock(ReplayEventDiffService.class);
        artifactReplayVerificationService = mock(ArtifactReplayVerificationService.class);
        replayFeaturePolicy = mock(ReplayFeaturePolicy.class);
        when(replayFeaturePolicy.requireEnabled(any(), any(), any()))
                .thenReturn(new ReplayFeaturePolicy.Decision(
                        true, ReplayMode.SIMULATION_REPLAY.code(), "full_rollout", null));
        service = new SimulationReplayService(
                agentRunService, eventRepository, eventQueryService, conversationRepository,
                replayInstanceService, replayEngine, replayEventDiffService,
                artifactReplayVerificationService,
                replayFeaturePolicy,
                mock(TaskTraceService.class), new ObjectMapper()
        );
        administrator = new SecurityUser(1L, "admin", "Admin", List.of("ADMIN"), List.of("trace"));
    }

    @Test
    void shouldFreezeEventsAndRecordedStepResultsBeforeExecution() {
        AgentRunResponse run = run();
        List<RuntimeEventEnvelope> events = List.of(
                event(1L, "message.delta", Map.of("content", "录制回复")),
                event(2L, "step.completed", Map.of("taskStepId", 100L))
        );
        when(agentRunService.get("RUN_1", administrator)).thenReturn(run);
        when(conversationRepository.findTaskById(20L)).thenReturn(Optional.of(task()));
        when(eventRepository.findLastSequence("RUN_1")).thenReturn(2L);
        when(eventQueryService.findAfter("RUN_1", 0L, 2, administrator))
                .thenReturn(new RuntimeEventQueryService.RuntimeEventBatch(run, events));
        when(conversationRepository.findTaskStepsByTaskId(20L)).thenReturn(List.of(step()));
        ArtifactVerificationReport artifactReport = emptyArtifactReport();
        when(artifactReplayVerificationService.capture(eq(20L), eq(events), any()))
                .thenReturn(new ArtifactReplayVerificationService.Capture(List.of(), artifactReport));
        ReplayInstance replay = new ReplayInstance(
                "REPLAY_1", "REPLAY_TRACE_1", ReplayMode.SIMULATION_REPLAY, Map.of("manifestVersion", 1));
        when(replayInstanceService.startSimulationReplay(any())).thenReturn(replay);
        ReplayExecutionResult executionResult = new ReplayExecutionResult(
                Map.of("assembledMessage", "录制回复", "providerInvocationCount", 0),
                List.of(Map.of("observationType", "tool_result"))
        );
        when(replayEngine.execute(any())).thenReturn(executionResult);
        ReplayEventDiff eventDiff = consistentDiff();
        when(replayEventDiffService.compare(eq(events), any(), eq(executionResult))).thenReturn(eventDiff);

        var response = service.replay("RUN_1", new SimulationReplayRequest(null, null), administrator);

        assertThat(response.replayMode()).isEqualTo("simulation_replay");
        verify(replayFeaturePolicy).requireEnabled(ReplayMode.SIMULATION_REPLAY, 30L, administrator);
        assertThat(response.eventRange()).containsEntry("fromSequence", 1L).containsEntry("toSequence", 2L);
        assertThat(response.output()).containsEntry("providerInvocationCount", 0);
        assertThat(response.eventDiff().consistent()).isTrue();
        assertThat(response.artifactVerification().consistent()).isTrue();
        ArgumentCaptor<ReplayInstanceService.StartSimulationReplayCommand> startCaptor =
                ArgumentCaptor.forClass(ReplayInstanceService.StartSimulationReplayCommand.class);
        verify(replayInstanceService).startSimulationReplay(startCaptor.capture());
        assertThat(startCaptor.getValue().events()).hasSize(2);
        assertThat(startCaptor.getValue().recordedStepResults()).singleElement().satisfies(result -> {
            assertThat(result.taskStepId()).isEqualTo(100L);
            assertThat(result.resultType()).isEqualTo("tool");
            assertThat(result.outputSha256()).hasSize(64);
        });
        verify(replayInstanceService).complete(eq(replay), anyMap(), eq(1L));
    }

    @Test
    void shouldStopBeforeSnapshotLoadingWhenFeaturePolicyRejectsRun() {
        AgentRunResponse run = run();
        when(agentRunService.get("RUN_1", administrator)).thenReturn(run);
        org.mockito.Mockito.doThrow(new com.zc.agent.core.common.exception.BusinessException("灰度未开放"))
                .when(replayFeaturePolicy)
                .requireEnabled(ReplayMode.SIMULATION_REPLAY, 30L, administrator);

        assertThatThrownBy(() -> service.replay(
                "RUN_1", new SimulationReplayRequest(null, null), administrator))
                .isInstanceOf(com.zc.agent.core.common.exception.BusinessException.class)
                .hasMessageContaining("灰度未开放");

        verifyNoInteractions(eventRepository, eventQueryService, replayInstanceService, replayEngine);
    }

    @Test
    void shouldFailWhenFrozenEventSequenceHasGap() {
        AgentRunResponse run = run();
        when(agentRunService.get("RUN_1", administrator)).thenReturn(run);
        when(conversationRepository.findTaskById(20L)).thenReturn(Optional.of(task()));
        when(eventRepository.findLastSequence("RUN_1")).thenReturn(2L);
        when(eventQueryService.findAfter("RUN_1", 0L, 2, administrator))
                .thenReturn(new RuntimeEventQueryService.RuntimeEventBatch(
                        run, List.of(event(2L, "message.delta", Map.of("content", "gap")))));

        assertThatThrownBy(() -> service.replay(
                "RUN_1", new SimulationReplayRequest(null, null), administrator
        )).hasMessage("Simulation Replay 来源事件序号不连续，期望 1");

        verifyNoInteractions(replayInstanceService, replayEngine);
    }

    @Test
    void shouldRejectNonAdministratorBeforeReadingRun() {
        SecurityUser user = new SecurityUser(2L, "user", "User", List.of("USER"), List.of());

        assertThatThrownBy(() -> service.replay(
                "RUN_1", new SimulationReplayRequest(null, null), user
        )).hasMessage("Simulation Replay 当前仅允许平台管理员执行");

        verifyNoInteractions(agentRunService, eventRepository, replayEngine);
    }

    private AgentRunResponse run() {
        LocalDateTime now = LocalDateTime.now();
        return new AgentRunResponse(
                "RUN_1", 10L, 30L, "completed", 1, 1, 0, 20L,
                null, false, now, now, now, now, "default", "[]"
        );
    }

    private TaskRecord task() {
        LocalDateTime now = LocalDateTime.now();
        return new TaskRecord(
                20L, "TASK_1", "TRACE_1", 10L, 1L, 30L,
                null, null, null, "root", "tool_only", 1, "[]",
                null, null, "success", now, now, null, now
        );
    }

    private TaskStepRecord step() {
        LocalDateTime now = LocalDateTime.now();
        return new TaskStepRecord(
                100L, 20L, 1, "tool", "查询", "success", 0, "{}",
                "{\"resultType\":\"tool\",\"responseText\":\"来自录制快照\"}", null,
                now, now, now
        );
    }

    private RuntimeEventEnvelope event(Long sequenceNo, String eventType, Map<String, Object> payload) {
        return new RuntimeEventEnvelope(
                "EVT_" + sequenceNo, sequenceNo, 10L, null, "RUN_1", 20L, null,
                eventType, LocalDateTime.now(), 1, payload, Map.of()
        );
    }

    private ReplayEventDiff consistentDiff() {
        return new ReplayEventDiff(
                1, true, new ReplayEventDiff.Summary(1, 1, 1, 0, 0, 0),
                new ReplayEventDiff.ResultDiff("matched", "录制回复", "录制回复", "hash", "hash", 4, 4),
                List.of(), List.of()
        );
    }

    private ArtifactVerificationReport emptyArtifactReport() {
        return new ArtifactVerificationReport(
                1, true, new ArtifactVerificationReport.Summary(0, 0, 0, 0), List.of(), List.of());
    }
}
