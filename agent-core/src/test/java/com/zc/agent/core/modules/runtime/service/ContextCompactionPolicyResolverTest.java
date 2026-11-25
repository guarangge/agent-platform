package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextCompactionPolicyResolverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContextCompactionPolicyResolver resolver = new ContextCompactionPolicyResolver();

    @Test
    void shouldPreferConversationPolicyOverAgentPolicy() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionContext.ConversationContext conversation = mock(ExecutionContext.ConversationContext.class);
        ExecutionContext.AgentContext agent = mock(ExecutionContext.AgentContext.class);
        when(context.conversationContext()).thenReturn(conversation);
        when(context.agentContext()).thenReturn(agent);
        when(conversation.metadata()).thenReturn(objectMapper.readTree("{\"compactionPolicy\":{\"maxRecentMessages\":9}}"));
        when(agent.metadata()).thenReturn(objectMapper.readTree("{\"compactionPolicy\":{\"maxRecentMessages\":3}}"));

        assertThat(resolver.resolve(context).maxRecentMessages()).isEqualTo(9);
    }

    @Test
    void shouldUseDefaultsWhenNoScopedPolicyExists() {
        assertThat(resolver.resolve(null)).isEqualTo(ContextCompactionPolicy.defaults());
    }
}
