package com.zc.agent.core.modules.runtime.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextStageModelTest {

    @Test
    void shouldResolveConfiguredStageModelAndFallbackToDefault() {
        LlmCallBudget budget = new LlmCallBudget(6);
        ExecutionContext.ModelContext defaultModel = model("DEFAULT", budget);
        ExecutionContext.ModelContext decisionModel = model("DECISION", budget);
        ExecutionContext context = new ExecutionContext(
                null, null, null, null, null, null, List.of(), null, null,
                null, null, null, null, defaultModel, Map.of("decision", decisionModel),
                null, null, List.of(), List.of(), List.of());

        assertThat(context.modelForStage("decision")).isSameAs(decisionModel);
        assertThat(context.modelForStage("reply")).isSameAs(defaultModel);
        assertThat(context.modelForStage("decision").callBudget()).isSameAs(defaultModel.callBudget());
        assertThat(context.hasRunnableModel("decision")).isTrue();
    }

    private ExecutionContext.ModelContext model(String code, LlmCallBudget budget) {
        return new ExecutionContext.ModelContext(
                code, code, "openai_compatible", "chat", "http://localhost", "key", null, code,
                0.2D, 100, 1000, 100, 100, 0.8D, "cl100k_base", 30, "enabled", budget);
    }
}
