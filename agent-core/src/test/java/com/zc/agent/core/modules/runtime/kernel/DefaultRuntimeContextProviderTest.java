package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRuntimeContextProviderTest {

    @Test
    void shouldResolveFrozenScopeAndResourceIdsFromExecutionContext() {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.currentUser()).thenReturn(new ExecutionContext.CurrentUserContext(
                10L, "tester", "Tester", List.of("ADMIN"), List.of(),
                20L, 21L, 22L, 23L));
        ExecutionContext.ConversationContext conversation = mock(ExecutionContext.ConversationContext.class);
        when(conversation.conversationId()).thenReturn(30L);
        when(executionContext.conversationContext()).thenReturn(conversation);
        ExecutionContext.AgentContext agent = mock(ExecutionContext.AgentContext.class);
        when(agent.agentId()).thenReturn(40L);
        when(executionContext.agentContext()).thenReturn(agent);

        RuntimeContextProvider.ResolvedRuntimeContext result =
                new DefaultRuntimeContextProvider().resolve(executionContext);

        assertThat(result.executionContext()).isSameAs(executionContext);
        assertThat(result.tenantId()).isEqualTo(20L);
        assertThat(result.organizationId()).isEqualTo(21L);
        assertThat(result.workspaceId()).isEqualTo(22L);
        assertThat(result.projectId()).isEqualTo(23L);
        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.conversationId()).isEqualTo(30L);
        assertThat(result.agentId()).isEqualTo(40L);
    }

    @Test
    void shouldKeepMissingOptionalContextsExplicitlyNull() {
        RuntimeContextProvider.ResolvedRuntimeContext result =
                new DefaultRuntimeContextProvider().resolve(null);

        assertThat(result.executionContext()).isNull();
        assertThat(result.tenantId()).isNull();
        assertThat(result.projectId()).isNull();
        assertThat(result.conversationId()).isNull();
        assertThat(result.agentId()).isNull();
    }
}
