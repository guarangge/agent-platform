package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentExecutionPolicyResolverTest {
    private final AgentExecutionPolicyResolver resolver = new AgentExecutionPolicyResolver();

    @Test
    void shouldResolveBoundedAgentPolicy() {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.putObject("plannerPolicy")
                .put("autoReplanEnabled", false)
                .put("maxReplanTimes", 99)
                .put("replanCircuitBreakerThreshold", 0)
                .put("minimumReplanQualityScore", 75);
        metadata.putObject("decisionPolicy").put("llmFailureAction", "fail_fast");
        metadata.withObject("decisionPolicy").put("mode", "rule_first");
        metadata.putObject("executionPolicy").put("maxLlmCallsPerRun", 100);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                1L, "A", "Agent", "MODEL", "enabled", metadata));

        AgentExecutionPolicyResolver.Policy policy = resolver.resolve(context);

        assertThat(policy.autoReplanEnabled()).isFalse();
        assertThat(policy.maxReplanTimes()).isEqualTo(20);
        assertThat(policy.replanCircuitBreakerThreshold()).isEqualTo(1);
        assertThat(policy.minimumReplanQualityScore()).isEqualTo(75);
        assertThat(policy.decisionLlmFailureAction()).isEqualTo("fail_fast");
        assertThat(policy.decisionMode()).isEqualTo("rule_first");
        assertThat(policy.maxLlmCallsPerRun()).isEqualTo(50);
    }

    @Test
    void shouldUseSafeDefaultsForInvalidPolicy() {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.putObject("decisionPolicy").put("llmFailureAction", "unknown");
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                1L, "A", "Agent", "MODEL", "enabled", metadata));

        AgentExecutionPolicyResolver.Policy policy = resolver.resolve(context);

        assertThat(policy.maxReplanTimes()).isEqualTo(3);
        assertThat(policy.minimumReplanQualityScore()).isEqualTo(60);
        assertThat(policy.decisionLlmFailureAction()).isEqualTo("rule_fallback");
        assertThat(policy.decisionMode()).isEqualTo("llm_first");
        assertThat(policy.planningMode()).isEqualTo("adaptive");
        assertThat(policy.skipPlannerForSingleWorkflow()).isTrue();
    }
}
