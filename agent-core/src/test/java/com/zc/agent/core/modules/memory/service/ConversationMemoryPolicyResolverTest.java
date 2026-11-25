package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryPolicyResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUsePlatformDefaultsWhenMemoryPolicyIsMissing() {
        var policy = ConversationMemoryPolicyResolver.resolveConversationPolicy(
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode()
        );

        assertThat(policy.userMemoryEnabled()).isTrue();
        assertThat(policy.userMemoryMode()).isEqualTo(ConversationMemoryPolicyResolver.MODE_SUMMARY_ONLY);
        assertThat(policy.userMemoryWritebackEnabled()).isTrue();
    }

    @Test
    void shouldApplyConversationOverrideOnAgentDefaults() throws Exception {
        var agentMetadata = objectMapper.readTree("""
                {"memoryPolicy":{"userMemoryEnabled":true,"userMemoryMode":"summary_and_facts","userMemoryWritebackEnabled":true}}
                """);
        var conversationMetadata = objectMapper.readTree("""
                {"memoryPolicy":{"userMemoryEnabled":false,"userMemoryMode":"off","userMemoryWritebackEnabled":false}}
                """);

        var policy = ConversationMemoryPolicyResolver.resolveConversationPolicy(conversationMetadata, agentMetadata);

        assertThat(policy.userMemoryEnabled()).isFalse();
        assertThat(policy.userMemoryMode()).isEqualTo(ConversationMemoryPolicyResolver.MODE_OFF);
        assertThat(policy.userMemoryWritebackEnabled()).isFalse();
        assertThat(policy.agentDefaultUserMemoryMode()).isEqualTo(ConversationMemoryPolicyResolver.MODE_SUMMARY_AND_FACTS);
    }
}
