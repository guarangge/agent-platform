package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveLoopBudgetPolicyResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InteractiveLoopBudgetPolicyResolver resolver = new InteractiveLoopBudgetPolicyResolver();

    @Test
    void shouldResolveConfiguredBudgetAndRespectRunLlmLimit() {
        ObjectNode metadata = objectMapper.createObjectNode();
        ObjectNode execution = metadata.putObject("executionPolicy");
        execution.put("maxLlmCallsPerRun", 4);
        execution.putObject("interactiveLoop")
                .put("maxIterations", 20)
                .put("maxLlmCalls", 8)
                .put("maxToolCalls", 30)
                .put("maxTokens", 90000)
                .put("maxCostAmount", 12.50)
                .put("duplicateToolCallLimit", 3);

        InteractiveLoopBudgetPolicy policy = resolver.resolve(context(metadata));

        assertThat(policy.maxIterations()).isEqualTo(20);
        assertThat(policy.maxLlmCalls()).isEqualTo(4);
        assertThat(policy.maxToolCalls()).isEqualTo(30);
        assertThat(policy.maxTokens()).isEqualTo(90000);
        assertThat(policy.maxCostAmount()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(policy.duplicateToolCallLimit()).isEqualTo(3);
    }

    @Test
    void shouldFallbackInvalidHistoricalValuesToSafeDefaults() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.putObject("executionPolicy").putObject("interactiveLoop")
                .put("maxIterations", 0)
                .put("maxTokens", 9_000_000)
                .put("maxCostAmount", -1);

        InteractiveLoopBudgetPolicy policy = resolver.resolve(context(metadata));

        assertThat(policy.maxIterations()).isEqualTo(12);
        assertThat(policy.maxLlmCalls()).isEqualTo(6);
        assertThat(policy.maxTokens()).isEqualTo(50000);
        assertThat(policy.maxCostAmount()).isEqualByComparingTo("5.00");
    }

    private ExecutionContext context(ObjectNode metadata) {
        return new ExecutionContext(
                null, null, null, null, null, null, List.of(), null,
                new ExecutionContext.AgentContext(1L, "agent", "Agent", "model", "enabled", metadata),
                null, null, null, null, null, Map.of(), null, null,
                List.of(), List.of(), List.of());
    }
}
