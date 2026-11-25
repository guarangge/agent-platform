package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopBudgetPolicy;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopBudgetState;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopMetrics;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InteractiveLoopRuntimeEventServiceTest {

    private final RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InteractiveLoopRuntimeEventService service = new InteractiveLoopRuntimeEventService(
            publisher, new InteractiveLoopMetrics(registry));

    @AfterEach
    void tearDown() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishSafeStopSnapshot() {
        AgentRunStreamingContext.bind("RUN_1");
        InteractiveLoopBudgetState state = new InteractiveLoopBudgetState(
                new InteractiveLoopBudgetPolicy(2, 2, 2, 100, BigDecimal.TEN, 1));
        state.consumeToolCall("SEARCH", "secret-parameter-hash");
        state.consumeToolCall("SEARCH", "secret-parameter-hash");

        service.stopped(10L, "TRACE_1", state.snapshot());

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.RUN_LOOP_STOPPED),
                argThat(payload -> "duplicate_tool_call".equals(payload.get("stopReason"))
                        && Integer.valueOf(1).equals(payload.get("uniqueToolFingerprints"))
                        && payload.values().stream().noneMatch(value -> String.valueOf(value).contains("secret"))),
                eq(java.util.Map.of("traceId", "TRACE_1")));
        assertThat(registry.get("agent.runtime.interactive.loop.stop")
                .tag("reason", "duplicate_tool_call").counter().count()).isEqualTo(1);
        assertThat(registry.get("agent.runtime.interactive.loop.tool.calls")
                .summary().totalAmount()).isEqualTo(1);
    }
}
