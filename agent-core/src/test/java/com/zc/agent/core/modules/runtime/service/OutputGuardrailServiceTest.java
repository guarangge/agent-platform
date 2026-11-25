package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.service.GuardrailPolicyHitService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutputGuardrailServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GuardrailPolicyHitService hitService = mock(GuardrailPolicyHitService.class);
    private final OutputGuardrailService service = new OutputGuardrailService(hitService, "test");

    @Test
    void shouldRedactMatchedOutputAndRecordHit() throws Exception {
        ExecutionContext context = context("""
                {"policyCenter":{"rules":[{
                  "policyCode":"mask.phone","policyName":"手机号脱敏","version":1,"enabled":true,
                  "priority":100,"layer":"global","stage":"output",
                  "scope":{},"rollout":{"mode":"all"},"match":{"mode":"keyword_match"},
                  "action":{"type":"redact"},
                  "output":{"patterns":["1[3-9]\\\\d{9}"],"replacement":"***"}
                }]}}
                """);

        String result = service.apply(context, "联系电话 13812345678", 10L, "TRACE_1", 1L);

        assertThat(result).isEqualTo("联系电话 ***");
        verify(hitService).recordHit(eq(20L), eq(10L), eq(null), eq(30L), eq("TRACE_1"),
                eq("redact"), anyMap(), eq(1L));
    }

    @Test
    void shouldIgnoreInputOnlyPolicy() throws Exception {
        ExecutionContext context = context("""
                {"policyCenter":{"rules":[{
                  "policyCode":"input.only","enabled":true,"priority":100,"stage":"input",
                  "scope":{},"rollout":{"mode":"all"},"match":{"mode":"always"},
                  "action":{"type":"block"},"output":{"safeResponse":"blocked"}
                }]}}
                """);

        assertThat(service.apply(context, "safe", 10L, "TRACE_1", 1L)).isEqualTo("safe");
    }

    private ExecutionContext context(String metadata) throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.currentUser()).thenReturn(new ExecutionContext.CurrentUserContext(1L, "zc", "ZC", List.of("ADMIN"), List.of()));
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                30L, "AGENT", "Agent", "MODEL", "enabled", objectMapper.readTree(metadata)
        ));
        when(context.conversationContext()).thenReturn(new ExecutionContext.ConversationContext(
                20L, "CONV", "Conversation", "enabled", null
        ));
        return context;
    }
}
