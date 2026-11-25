package com.zc.agent.core.modules.runtime.plugin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimePluginVersionMatcherTest {

    private final RuntimePluginVersionMatcher matcher = new RuntimePluginVersionMatcher();

    @Test
    void shouldMatchCommonSemanticVersionConstraints() {
        assertThat(matcher.matches("1.2.3", "1.2.3")).isTrue();
        assertThat(matcher.matches("1.8.0", "^1.2.3")).isTrue();
        assertThat(matcher.matches("2.0.0", "^1.2.3")).isFalse();
        assertThat(matcher.matches("0.2.8", "^0.2.3")).isTrue();
        assertThat(matcher.matches("0.3.0", "^0.2.3")).isFalse();
        assertThat(matcher.matches("1.2.9", "~1.2.3")).isTrue();
        assertThat(matcher.matches("1.3.0", "~1.2.3")).isFalse();
        assertThat(matcher.matches("1.5.0", ">=1.0.0 <2.0.0")).isTrue();
        assertThat(matcher.matches("1.9.0", "1.x")).isTrue();
        assertThat(matcher.matches("2.1.0", "1.x || 2.1.x")).isTrue();
    }

    @Test
    void shouldApplySemanticPrereleaseOrderingAndRejectInvalidConstraints() {
        assertThat(matcher.matches("1.0.0", ">1.0.0-rc.1")).isTrue();
        assertThat(matcher.matches("1.0.0-beta.2", ">1.0.0-beta.1 <1.0.0")).isTrue();

        assertThatThrownBy(() -> matcher.matches("1.0.0", ">=1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matcher.matches("1.0.0", "1.0.0 ||"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matcher.matches("1.0.0", "1.0.0 || invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matcher.matches("1.0.0", ">=2.0.0 invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matcher.matches("1.0.0-01", ">=1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
