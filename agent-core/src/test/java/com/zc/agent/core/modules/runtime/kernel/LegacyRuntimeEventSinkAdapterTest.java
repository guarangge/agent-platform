package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.service.AgentRunStreamEventService;
import com.zc.agent.core.modules.runtime.protocol.service.TaskStepLifecycleEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyRuntimeEventSinkAdapterTest {

    private AgentRunStreamEventService agentRunStreamEventService;
    private TaskStepLifecycleEventService taskStepLifecycleEventService;
    private LegacyRuntimeEventSinkAdapter adapter;

    @BeforeEach
    void setUp() {
        agentRunStreamEventService = mock(AgentRunStreamEventService.class);
        taskStepLifecycleEventService = mock(TaskStepLifecycleEventService.class);
        adapter = new LegacyRuntimeEventSinkAdapter(
                agentRunStreamEventService,
                taskStepLifecycleEventService
        );
    }

    @Test
    void shouldDelegateProgressAndAtomicStepLifecycleCommands() {
        RuntimeEventSink.ProgressEvent progress = new RuntimeEventSink.ProgressEvent(
                "step", "正在执行步骤 1/2：查询", Map.of("stepNo", 1));
        RuntimeEventSink.StepStartedEvent started = new RuntimeEventSink.StepStartedEvent(
                10L, 1, "STEP_1", 1, 2, "tool", "查询", "{}", 20L);
        TaskStepLifecycleEventService.StartStepCommand legacyStarted =
                new TaskStepLifecycleEventService.StartStepCommand(
                        10L, 1, "STEP_1", 1, 2, "tool", "查询", "{}", 20L);
        when(taskStepLifecycleEventService.start(legacyStarted)).thenReturn(30L);
        RuntimeEventSink.StepTransitionedEvent transitioned = new RuntimeEventSink.StepTransitionedEvent(
                10L, 30L, "STEP_1", 1, 2, "tool", "查询", "success",
                "{\"resultType\":\"tool\"}", null, null, false, 20L);
        TaskStepLifecycleEventService.TransitionStepCommand legacyTransitioned =
                new TaskStepLifecycleEventService.TransitionStepCommand(
                        10L, 30L, "STEP_1", 1, 2, "tool", "查询", "success",
                        "{\"resultType\":\"tool\"}", null, null, false, 20L);

        adapter.progress(progress);
        Long taskStepId = adapter.stepStarted(started);
        adapter.stepTransitioned(transitioned);

        assertThat(adapter.sinkCode()).isEqualTo(LegacyRuntimeEventSinkAdapter.SINK_CODE);
        assertThat(taskStepId).isEqualTo(30L);
        verify(agentRunStreamEventService).publishProgress(
                progress.stage(), progress.message(), progress.metadata());
        verify(taskStepLifecycleEventService).start(legacyStarted);
        verify(taskStepLifecycleEventService).transition(legacyTransitioned);
    }

    @Test
    void shouldPropagateAtomicLifecycleFailureToRollbackCaller() {
        RuntimeEventSink.StepTransitionedEvent transitioned = new RuntimeEventSink.StepTransitionedEvent(
                10L, 30L, "STEP_1", 1, 1, "tool", "查询", "failed",
                "{}", "event store unavailable", "tool_execution_failed", false, 20L);
        TaskStepLifecycleEventService.TransitionStepCommand legacyCommand =
                new TaskStepLifecycleEventService.TransitionStepCommand(
                        10L, 30L, "STEP_1", 1, 1, "tool", "查询", "failed",
                        "{}", "event store unavailable", "tool_execution_failed", false, 20L);
        IllegalStateException failure = new IllegalStateException("event store unavailable");
        doThrow(failure).when(taskStepLifecycleEventService).transition(legacyCommand);

        assertThatThrownBy(() -> adapter.stepTransitioned(transitioned)).isSameAs(failure);
    }
}
