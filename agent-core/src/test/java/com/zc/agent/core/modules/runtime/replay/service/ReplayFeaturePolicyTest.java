package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.ReplayFeatureProperties;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayFeaturePolicyTest {

    @Test
    void shouldSupportMasterAndModeRollbackSwitches() {
        ReplayFeatureProperties properties = defaults();
        ReplayFeaturePolicy policy = new ReplayFeaturePolicy(properties);

        properties.setEnabled(false);
        assertThat(policy.decide(ReplayMode.PROMPT_REPLAY, 10L, 1L).reason())
                .isEqualTo("master_disabled");

        properties.setEnabled(true);
        properties.setSimulationEnabled(false);
        assertThat(policy.decide(ReplayMode.SIMULATION_REPLAY, 10L, 1L).reason())
                .isEqualTo("simulation_replay_disabled");
        assertThat(policy.decide(ReplayMode.PROMPT_REPLAY, 10L, 1L).enabled()).isTrue();
        assertThat(policy.decide(ReplayMode.EXECUTION_REPLAY, 10L, 1L).reason())
                .isEqualTo("execution_replay_not_available");
    }

    @Test
    void shouldApplyProjectAndAgentAllowlistsBeforePercentageRollout() {
        ReplayFeatureProperties properties = defaults();
        properties.setProjectIds(List.of(1L));
        properties.setAgentIds(List.of(10L));
        properties.setRolloutPercent(0);
        ReplayFeaturePolicy policy = new ReplayFeaturePolicy(properties);

        assertThat(policy.decide(ReplayMode.SIMULATION_REPLAY, 10L, 1L))
                .satisfies(decision -> {
                    assertThat(decision.enabled()).isTrue();
                    assertThat(decision.reason()).isEqualTo("agent_allowlisted");
                });
        assertThat(policy.decide(ReplayMode.SIMULATION_REPLAY, 11L, 1L).reason())
                .isEqualTo("agent_not_allowlisted");
        assertThat(policy.decide(ReplayMode.SIMULATION_REPLAY, 10L, 2L).reason())
                .isEqualTo("project_not_allowlisted");
    }

    @Test
    void shouldProduceStablePercentageDecision() {
        ReplayFeatureProperties properties = defaults();
        properties.setRolloutPercent(35);
        ReplayFeaturePolicy policy = new ReplayFeaturePolicy(properties);

        var first = policy.decide(ReplayMode.SIMULATION_REPLAY, 10L, 1L);
        var second = policy.decide(ReplayMode.SIMULATION_REPLAY, 10L, 1L);

        assertThat(first).isEqualTo(second);
        assertThat(first.rolloutBucket()).isBetween(0, 99);
        assertThat(first.enabled()).isEqualTo(first.rolloutBucket() < 35);
    }

    @Test
    void shouldRejectDisabledExecutionAtBackendBoundary() {
        ReplayFeatureProperties properties = defaults();
        properties.setRolloutPercent(0);
        ReplayFeaturePolicy policy = new ReplayFeaturePolicy(properties);
        SecurityUser administrator = new SecurityUser(
                1L, "admin", "Admin", List.of("ADMIN"), List.of("tracing"));

        assertThatThrownBy(() -> policy.requireEnabled(
                ReplayMode.SIMULATION_REPLAY, 10L, administrator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rollout_excluded");
    }

    private ReplayFeatureProperties defaults() {
        return new ReplayFeatureProperties();
    }
}
