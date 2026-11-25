package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.kernel.RuntimeExecutionModePolicy;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionMode;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RuntimeExecutionModeEventServiceTest {

    private final RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
    private final RuntimeExecutionModeEventService service = new RuntimeExecutionModeEventService(publisher);

    @AfterEach
    void tearDown() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishSafeModeSelectionForBoundRun() {
        AgentRunStreamingContext.bind("RUN_1");
        RuntimeExecutionModePolicy.Selection selection = new RuntimeExecutionModePolicy.Selection(
                RuntimeExecutionMode.INTERACTIVE_LOOP, RuntimeExecutionMode.WORKFLOW_PLAN,
                List.of(RuntimeExecutionMode.WORKFLOW_PLAN, RuntimeExecutionMode.INTERACTIVE_LOOP),
                "interactive_loop_rollout_excluded", true, 88);

        service.selected(10L, "TRACE_1", selection);

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.RUN_MODE_SELECTED),
                argThat(payload -> Boolean.TRUE.equals(payload.get("fallback"))
                        && "workflow_plan".equals(payload.get("effectiveMode"))
                        && Integer.valueOf(88).equals(payload.get("rolloutBucket"))),
                eq(java.util.Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldSkipEventWithoutBoundRun() {
        service.selected(10L, "TRACE_1", new RuntimeExecutionModePolicy.Selection(
                RuntimeExecutionMode.WORKFLOW_PLAN, RuntimeExecutionMode.WORKFLOW_PLAN,
                List.of(RuntimeExecutionMode.WORKFLOW_PLAN), "agent_default", false, null));

        verifyNoInteractions(publisher);
    }
}
