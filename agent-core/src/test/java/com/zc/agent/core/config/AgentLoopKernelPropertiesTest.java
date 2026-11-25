package com.zc.agent.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopKernelPropertiesTest {

    @Test
    void shouldBindKernelFeatureFlagAndAgentAllowlist() {
        AgentLoopKernelProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "core.runtime.kernel.enabled", "true",
                "core.runtime.kernel.rollout-percent", "25",
                "core.runtime.kernel.agent-ids", "10,12",
                "core.runtime.kernel.project-ids", "20,22"
        ))).bind("core.runtime.kernel", Bindable.of(AgentLoopKernelProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getRolloutPercent()).isEqualTo(25);
        assertThat(properties.getAgentIds()).containsExactly(10L, 12L);
        assertThat(properties.getProjectIds()).containsExactly(20L, 22L);
    }

    @Test
    void shouldBindIndependentInteractiveLoopFeatureFlag() {
        InteractiveLoopProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "core.runtime.interactive-loop.enabled", "true",
                "core.runtime.interactive-loop.kill-switch", "true",
                "core.runtime.interactive-loop.rollout-percent", "15",
                "core.runtime.interactive-loop.agent-ids", "10,12",
                "core.runtime.interactive-loop.project-ids", "20"
        ))).bind("core.runtime.interactive-loop", Bindable.of(InteractiveLoopProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isKillSwitch()).isTrue();
        assertThat(properties.getRolloutPercent()).isEqualTo(15);
        assertThat(properties.getAgentIds()).containsExactly(10L, 12L);
        assertThat(properties.getProjectIds()).containsExactly(20L);
    }
}
