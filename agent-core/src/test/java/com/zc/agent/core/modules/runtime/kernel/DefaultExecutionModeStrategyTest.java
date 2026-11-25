package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.config.AgentLoopKernelProperties;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultExecutionModeStrategyTest {

    @Test
    void shouldDefaultToLegacyDirectPath() {
        AgentLoopKernelProperties properties = new AgentLoopKernelProperties();
        DefaultExecutionModeStrategy strategy = new DefaultExecutionModeStrategy(properties);

        ExecutionModeStrategy.Selection selection = strategy.select(request(10L, 12L));

        assertThat(selection.kernelEnabled()).isFalse();
        assertThat(selection.reason()).isEqualTo("kernel_disabled");
    }

    @Test
    void shouldPreferAgentAllowlistAndKeepPercentageStable() {
        AgentLoopKernelProperties properties = new AgentLoopKernelProperties();
        properties.setEnabled(true);
        properties.setAgentIds(List.of(10L));
        properties.setRolloutPercent(0);
        DefaultExecutionModeStrategy strategy = new DefaultExecutionModeStrategy(properties);

        assertThat(strategy.select(request(10L, 12L)).reason()).isEqualTo("agent_allowlisted");
        assertThat(strategy.select(request(11L, 12L)).reason()).isEqualTo("agent_not_allowlisted");

        properties.setAgentIds(List.of());
        properties.setRolloutPercent(35);
        var first = strategy.select(request(10L, 12L));
        var second = strategy.select(request(10L, 12L));
        assertThat(first).isEqualTo(second);
        assertThat(first.rolloutBucket()).isBetween(0, 99);
        assertThat(first.kernelEnabled()).isEqualTo(first.rolloutBucket() < 35);
    }

    @Test
    void shouldApplyProjectAllowlistBeforeAgentRollout() {
        AgentLoopKernelProperties properties = new AgentLoopKernelProperties();
        properties.setEnabled(true);
        properties.setProjectIds(List.of(12L));
        properties.setAgentIds(List.of(10L));
        DefaultExecutionModeStrategy strategy = new DefaultExecutionModeStrategy(properties);

        assertThat(strategy.select(request(10L, 12L)).reason()).isEqualTo("agent_allowlisted");
        assertThat(strategy.select(request(10L, 13L)).reason()).isEqualTo("project_not_allowlisted");
        assertThat(strategy.select(request(10L, null)).reason()).isEqualTo("project_not_allowlisted");
    }

    private AgentLoopExecutionRequest request(Long agentId, Long projectId) {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                agentId, "AGENT_" + agentId, "Agent", "MODEL", "enabled", null));
        var executionRequest = new RuntimeExecutionRequest(
                context, null, null, 20L, "TRACE_1", "{}", "Bearer token", 1L,
                1, 0, List.of(), null, null, null);
        var runtimeContext = new RuntimeContextProvider.ResolvedRuntimeContext(
                context, 1L, 1L, 1L, projectId, 1L, 100L, agentId);
        return new AgentLoopExecutionRequest(executionRequest, runtimeContext);
    }
}
