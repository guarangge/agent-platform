package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceAnalyticsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayInstance;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.service.ReplayInstanceService;
import com.zc.agent.core.modules.runtime.replay.service.ReplayFeaturePolicy;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceInvestigationServiceTest {

    private ConversationRepository conversationRepository;
    private RegistryAgentClient registryAgentClient;
    private RuntimeLlmGatewayService runtimeLlmGatewayService;
    private TaskTraceService taskTraceService;
    private ReplayInstanceService replayInstanceService;
    private ReplayFeaturePolicy replayFeaturePolicy;
    private TraceInvestigationService service;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        registryAgentClient = mock(RegistryAgentClient.class);
        runtimeLlmGatewayService = mock(RuntimeLlmGatewayService.class);
        taskTraceService = mock(TaskTraceService.class);
        replayInstanceService = mock(ReplayInstanceService.class);
        replayFeaturePolicy = mock(ReplayFeaturePolicy.class);
        when(replayFeaturePolicy.requireEnabled(any(), any(), any()))
                .thenReturn(new ReplayFeaturePolicy.Decision(
                        true, ReplayMode.PROMPT_REPLAY.code(), "full_rollout", null));
        service = new TraceInvestigationService(
                conversationRepository,
                registryAgentClient,
                runtimeLlmGatewayService,
                taskTraceService,
                replayInstanceService,
                replayFeaturePolicy,
                new ObjectMapper()
        );
        securityUser = new SecurityUser(1L, "tester", "Tester", List.of("ADMIN"), List.of("trace"));
    }

    @Test
    void shouldExtractPromptSnapshotAndToolReference() {
        TaskTraceAnalyticsRecord record = traceRecord(
                "{\"inputObservation\":{\"rawLength\":120,\"finalLength\":80},\"toolCode\":\"DOC_SEARCH\"}",
                "{\"responseText\":\"原始回答\"}",
                "{\"targetToolCode\":\"DOC_SEARCH\",\"contextCompaction\":{\"stage\":\"planning\",\"promptSnapshot\":{\"systemPrompt\":\"system\",\"userPrompt\":\"user\"}}}"
        );
        when(conversationRepository.findTaskTraceAnalyticsById(1L, 100L)).thenReturn(Optional.of(record));

        var response = service.investigate(100L, securityUser);

        assertThat(response.replayable()).isTrue();
        assertThat(response.promptSnapshot()).containsEntry("systemPrompt", "system");
        assertThat(response.toolInputObservation()).containsEntry("rawLength", 120);
        assertThat(response.toolReferences()).extracting(item -> item.get("toolCode")).containsExactly("DOC_SEARCH");
    }

    @Test
    void shouldExtractStepPromptTraceFromOutputMetadata() {
        TaskTraceAnalyticsRecord record = traceRecord(
                "{}",
                "{\"metadata\":{\"promptTrace\":{\"stage\":\"reply\",\"promptSnapshot\":{\"systemPrompt\":\"reply-system\",\"userPrompt\":\"reply-user\"}}}}",
                "{}"
        );
        when(conversationRepository.findTaskTraceAnalyticsById(1L, 100L)).thenReturn(Optional.of(record));

        var response = service.investigate(100L, securityUser);

        assertThat(response.replayable()).isTrue();
        assertThat(response.promptSnapshot()).containsEntry("userPrompt", "reply-user");
    }

    @Test
    void shouldCreateIndependentPromptReplayInstance() throws Exception {
        TaskTraceAnalyticsRecord record = traceRecord(
                "{}", "{\"responseText\":\"原始回答\"}",
                "{\"contextCompaction\":{\"promptSnapshot\":{\"systemPrompt\":\"system\",\"userPrompt\":\"user\"}}}"
        );
        when(conversationRepository.findTaskTraceAnalyticsById(1L, 100L)).thenReturn(Optional.of(record));
        when(conversationRepository.findTaskById(20L)).thenReturn(Optional.of(taskRecord()));
        when(registryAgentClient.getEnabledAgentSnapshot(30L, "Bearer token"))
                .thenReturn(new AgentSnapshot(30L, "AGENT", "Agent", "MODEL", null, null,
                        null, null, null, null, "enabled", new ObjectMapper().createObjectNode()));
        ExecutionContext.ModelContext model = modelContext();
        when(registryAgentClient.getModelSnapshot("MODEL", "Bearer token")).thenReturn(model);
        ReplayInstance replay = new ReplayInstance(
                "REPLAY_1", "REPLAY_TRACE_1", ReplayMode.PROMPT_REPLAY, java.util.Map.of("manifestVersion", 1));
        when(replayInstanceService.startPromptReplay(org.mockito.ArgumentMatchers.any())).thenReturn(replay);
        when(runtimeLlmGatewayService.chatCompletionResult("system", "user", model))
                .thenReturn(new LlmCompletionResult("新回答", "mock", "mock-model", 10, 5, 15, 20L));

        var response = service.replay(100L, new com.zc.agent.core.modules.conversation.dto.TraceReplayRequest(
                null, null, null), "Bearer token", securityUser);

        assertThat(response.replayCode()).isEqualTo("REPLAY_1");
        assertThat(response.replayMode()).isEqualTo("prompt_replay");
        assertThat(response.replayTraceId()).isEqualTo("REPLAY_TRACE_1");
        assertThat(response.traceId()).isEqualTo("TRACE_1");
        assertThat(response.replayOutput()).isEqualTo("新回答");
        verify(replayFeaturePolicy).requireEnabled(ReplayMode.PROMPT_REPLAY, 30L, securityUser);
        verify(replayInstanceService).complete(
                org.mockito.ArgumentMatchers.eq(replay), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(1L));
        verify(taskTraceService).recordSpan(
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq("REPLAY_TRACE_1"),
                org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.eq("prompt_replay"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("success"),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void shouldStopBeforeModelLoadingWhenPromptReplayFeatureIsDisabled() {
        TaskTraceAnalyticsRecord record = traceRecord("{}", "{}", "{}");
        when(conversationRepository.findTaskTraceAnalyticsById(1L, 100L)).thenReturn(Optional.of(record));
        when(replayFeaturePolicy.requireEnabled(ReplayMode.PROMPT_REPLAY, 30L, securityUser))
                .thenThrow(new com.zc.agent.core.common.exception.BusinessException("Prompt Replay 已关闭"));

        assertThatThrownBy(() -> service.replay(
                100L,
                new com.zc.agent.core.modules.conversation.dto.TraceReplayRequest(null, null, null),
                "Bearer token",
                securityUser
        )).hasMessage("Prompt Replay 已关闭");

        verifyNoInteractions(registryAgentClient, replayInstanceService, runtimeLlmGatewayService);
    }

    @Test
    void shouldRejectPromptReplayForNonAdministrator() {
        SecurityUser normalUser = new SecurityUser(2L, "user", "User", List.of("USER"), List.of("trace"));

        assertThatThrownBy(() -> service.replay(
                100L,
                new com.zc.agent.core.modules.conversation.dto.TraceReplayRequest(null, null, null),
                "Bearer token",
                normalUser
        )).hasMessage("Replay 当前仅允许平台管理员执行");

        verifyNoInteractions(conversationRepository, replayInstanceService, runtimeLlmGatewayService);
    }

    private TaskRecord taskRecord() {
        LocalDateTime now = LocalDateTime.now();
        return new TaskRecord(20L, "TASK_1", "TRACE_1", 10L, 1L, 30L,
                null, null, null, "root", "workflow_plan", 1, "[]",
                null, null, "success", now, now, null, now);
    }

    private ExecutionContext.ModelContext modelContext() {
        return new ExecutionContext.ModelContext(
                "MODEL", "Model", "mock", "chat", "http://secret", "secret-key", "/chat", "mock-model",
                0.2D, 1024, 8192, 1024, 256, 0.8D, "cl100k", 30, "enabled", null
        );
    }

    private TaskTraceAnalyticsRecord traceRecord(String input, String output, String metadata) {
        LocalDateTime now = LocalDateTime.now();
        return new TaskTraceAnalyticsRecord(
                100L, 10L, "CONV_1", 20L, "TASK_1", 30L, "Agent",
                "TRACE_1", 40L, 1, "步骤", "tool", "工具执行", "success",
                100L, null, input, output, metadata, now, now, now
        );
    }
}
