package com.zc.agent.core.modules.runtime.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStreamingContextTest {

    @AfterEach
    void clearContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldOnlyEnableStreamingInsideFinalResponseScope() {
        AgentRunStreamingContext.bind("RUN_1");
        AgentRunStreamingContext.configureResponseStreaming(true);

        assertThat(AgentRunStreamingContext.currentRunCode()).isEqualTo("RUN_1");
        assertThat(AgentRunStreamingContext.isResponseStreamingEnabled()).isTrue();
        assertThat(AgentRunStreamingContext.isFinalResponseScope()).isFalse();
        try (AgentRunStreamingContext.Scope ignored = AgentRunStreamingContext.openFinalResponseScope(true)) {
            assertThat(AgentRunStreamingContext.isFinalResponseScope()).isTrue();
        }
        assertThat(AgentRunStreamingContext.isFinalResponseScope()).isFalse();
    }
}
