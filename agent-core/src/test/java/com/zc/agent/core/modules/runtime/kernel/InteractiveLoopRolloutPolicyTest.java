package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.config.InteractiveLoopProperties;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopMetrics;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveLoopRolloutPolicyTest {

    @Test
    void shouldDefaultOffAndHonorProjectThenAgentAllowlist() {
        InteractiveLoopProperties properties = new InteractiveLoopProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InteractiveLoopRolloutPolicy policy = policy(properties, registry);
        assertThat(policy.select(request(10L, 20L)).reason()).isEqualTo("interactive_loop_disabled");
        assertThat(registry.get("agent.runtime.interactive.loop.route")
                .tag("outcome", "fallback")
                .tag("reason", "interactive_loop_disabled")
                .counter().count()).isEqualTo(1);

        properties.setEnabled(true);
        properties.setProjectIds(List.of(20L));
        properties.setAgentIds(List.of(10L));
        assertThat(policy.select(request(10L, 20L)).enabled()).isTrue();
        assertThat(policy.select(request(10L, 21L)).reason())
                .isEqualTo("interactive_loop_project_not_allowlisted");
        assertThat(policy.select(request(11L, 20L)).reason())
                .isEqualTo("interactive_loop_agent_not_allowlisted");
    }

    @Test
    void shouldLetKillSwitchOverrideEnabledRollout() {
        InteractiveLoopProperties properties = new InteractiveLoopProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(true);
        properties.setRolloutPercent(100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        InteractiveLoopRolloutPolicy.Selection selection = policy(properties, registry)
                .select(request(10L, 20L));

        assertThat(selection.enabled()).isFalse();
        assertThat(selection.reason()).isEqualTo("interactive_loop_kill_switched");
        assertThat(registry.get("agent.runtime.interactive.loop.route")
                .tag("outcome", "fallback")
                .tag("reason", "interactive_loop_kill_switched")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void shouldKeepPercentageBucketStable() {
        InteractiveLoopProperties properties = new InteractiveLoopProperties();
        properties.setEnabled(true);
        properties.setRolloutPercent(35);
        InteractiveLoopRolloutPolicy policy = policy(properties, new SimpleMeterRegistry());

        InteractiveLoopRolloutPolicy.Selection first = policy.select(request(10L, 20L));
        InteractiveLoopRolloutPolicy.Selection second = policy.select(request(10L, 20L));

        assertThat(first).isEqualTo(second);
        assertThat(first.rolloutBucket()).isBetween(0, 99);
        assertThat(first.enabled()).isEqualTo(first.rolloutBucket() < 35);
    }

    private InteractiveLoopRolloutPolicy policy(InteractiveLoopProperties properties,
                                                SimpleMeterRegistry registry) {
        return new InteractiveLoopRolloutPolicy(properties, new InteractiveLoopMetrics(registry));
    }

    private AgentLoopExecutionRequest request(Long agentId, Long projectId) {
        RuntimeExecutionRequest executionRequest = new RuntimeExecutionRequest(
                null, null, null, 1L, "TRACE_1", "{}", null, 1L,
                1, 0, List.of(), null, null, null);
        return new AgentLoopExecutionRequest(executionRequest,
                new RuntimeContextProvider.ResolvedRuntimeContext(
                        null, 1L, 1L, 1L, projectId, 1L, 1L, agentId));
    }
}
