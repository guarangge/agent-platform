package com.zc.agent.core.modules.runtime.replay.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayModeTest {

    @Test
    void shouldOnlyEnablePromptReplayInFirstM2Slice() {
        assertThat(ReplayMode.fromCode("prompt_replay")).isEqualTo(ReplayMode.PROMPT_REPLAY);
        assertThat(ReplayMode.PROMPT_REPLAY.enabled()).isTrue();
        assertThat(ReplayMode.SIMULATION_REPLAY.enabled()).isTrue();
        assertThat(ReplayMode.EXECUTION_REPLAY.enabled()).isFalse();
    }
}
