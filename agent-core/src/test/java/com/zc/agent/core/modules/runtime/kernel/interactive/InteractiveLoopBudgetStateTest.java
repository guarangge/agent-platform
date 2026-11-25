package com.zc.agent.core.modules.runtime.kernel.interactive;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveLoopBudgetStateTest {

    @Test
    void shouldStopBeforeExceedingEachHardBudget() {
        InteractiveLoopBudgetState iterationState = state(1, 2, 2, 100, "10", 2);
        assertThat(iterationState.consumeIteration().allowed()).isTrue();
        assertStopped(iterationState.consumeIteration(), InteractiveLoopStopReason.MAX_ITERATIONS);
        assertThat(iterationState.snapshot().iterations()).isEqualTo(1);

        InteractiveLoopBudgetState llmState = state(2, 1, 2, 100, "10", 2);
        assertThat(llmState.consumeLlmCall(40, new BigDecimal("1.5")).allowed()).isTrue();
        assertStopped(llmState.consumeLlmCall(1, BigDecimal.ZERO), InteractiveLoopStopReason.MAX_LLM_CALLS);
        assertThat(llmState.snapshot().llmCalls()).isEqualTo(1);

        InteractiveLoopBudgetState tokenState = state(2, 2, 2, 50, "10", 2);
        assertStopped(tokenState.consumeLlmCall(51, BigDecimal.ZERO), InteractiveLoopStopReason.MAX_TOKENS);
        assertThat(tokenState.snapshot().tokens()).isZero();

        InteractiveLoopBudgetState costState = state(2, 2, 2, 100, "1.00", 2);
        assertStopped(costState.consumeLlmCall(1, new BigDecimal("1.01")), InteractiveLoopStopReason.MAX_COST);
        assertThat(costState.snapshot().costAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        InteractiveLoopBudgetState toolState = state(2, 2, 1, 100, "10", 2);
        assertThat(toolState.consumeToolCall("SEARCH", "hash-a").allowed()).isTrue();
        assertStopped(toolState.consumeToolCall("OTHER", "hash-b"), InteractiveLoopStopReason.MAX_TOOL_CALLS);
        assertThat(toolState.snapshot().toolCalls()).isEqualTo(1);
    }

    @Test
    void shouldStopRepeatedToolFingerprintWithoutLeakingItToSnapshot() {
        InteractiveLoopBudgetState state = state(5, 5, 5, 100, "10", 2);
        assertThat(state.consumeToolCall("SEARCH", "ABC").allowed()).isTrue();
        assertThat(state.consumeToolCall("SEARCH", "abc").allowed()).isTrue();

        InteractiveLoopBudgetState.ConsumptionResult result = state.consumeToolCall("SEARCH", "abc");

        assertStopped(result, InteractiveLoopStopReason.DUPLICATE_TOOL_CALL);
        assertThat(result.snapshot().toolCalls()).isEqualTo(2);
        assertThat(result.snapshot().uniqueToolFingerprints()).isEqualTo(1);
        assertThat(result.snapshot().toString()).doesNotContain("SEARCH", "abc");
    }

    @Test
    void shouldKeepFirstStopReasonImmutable() {
        InteractiveLoopBudgetState state = state(1, 1, 1, 10, "1", 1);
        state.stop(InteractiveLoopStopReason.CAPABILITY_POLICY);

        InteractiveLoopBudgetState.ConsumptionResult result = state.stop(InteractiveLoopStopReason.FAILED);

        assertStopped(result, InteractiveLoopStopReason.CAPABILITY_POLICY);
        assertThat(state.consumeIteration().stopReason()).isEqualTo(InteractiveLoopStopReason.CAPABILITY_POLICY);
    }

    @Test
    void shouldRestoreCountersAndDuplicateFingerprints() {
        InteractiveLoopBudgetState original = state(5, 5, 5, 100, "10", 2);
        original.consumeIteration();
        original.consumeLlmCall(20, new BigDecimal("0.5"));
        original.consumeToolCall("SEARCH", "hash-a");

        InteractiveLoopBudgetState restored = new InteractiveLoopBudgetState(original.persistentSnapshot());

        assertThat(restored.snapshot().iterations()).isEqualTo(1);
        assertThat(restored.snapshot().llmCalls()).isEqualTo(1);
        assertThat(restored.snapshot().toolCalls()).isEqualTo(1);
        assertThat(restored.consumeToolCall("SEARCH", "hash-a").allowed()).isTrue();
        assertStopped(restored.consumeToolCall("SEARCH", "hash-a"),
                InteractiveLoopStopReason.DUPLICATE_TOOL_CALL);
    }

    private InteractiveLoopBudgetState state(int iterations,
                                             int llmCalls,
                                             int toolCalls,
                                             long tokens,
                                             String cost,
                                             int duplicateLimit) {
        return new InteractiveLoopBudgetState(new InteractiveLoopBudgetPolicy(
                iterations, llmCalls, toolCalls, tokens, new BigDecimal(cost), duplicateLimit));
    }

    private void assertStopped(InteractiveLoopBudgetState.ConsumptionResult result,
                               InteractiveLoopStopReason reason) {
        assertThat(result.allowed()).isFalse();
        assertThat(result.stopReason()).isEqualTo(reason);
    }
}
