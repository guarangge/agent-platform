package com.zc.agent.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayFeaturePropertiesTest {

    @Test
    void shouldBindCommaSeparatedAllowlistsAndModeSwitches() {
        ReplayFeatureProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "core.runtime.replay.enabled", "true",
                "core.runtime.replay.prompt-enabled", "false",
                "core.runtime.replay.rollout-percent", "25",
                "core.runtime.replay.agent-ids", "10,12",
                "core.runtime.replay.project-ids", "1,2"
        ))).bind("core.runtime.replay", Bindable.of(ReplayFeatureProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isPromptEnabled()).isFalse();
        assertThat(properties.getRolloutPercent()).isEqualTo(25);
        assertThat(properties.getAgentIds()).containsExactly(10L, 12L);
        assertThat(properties.getProjectIds()).containsExactly(1L, 2L);
    }

    @Test
    void shouldBindEmptyAllowlistsAsUnrestricted() {
        ReplayFeatureProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "core.runtime.replay.agent-ids", "",
                "core.runtime.replay.project-ids", ""
        ))).bind("core.runtime.replay", Bindable.of(ReplayFeatureProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.getAgentIds()).isEmpty();
        assertThat(properties.getProjectIds()).isEmpty();
    }
}
