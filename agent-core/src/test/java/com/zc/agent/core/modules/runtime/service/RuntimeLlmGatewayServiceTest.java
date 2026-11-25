package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.model.LlmCallBudget;
import com.zc.agent.core.modules.cost.service.BudgetExceededException;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeLlmGatewayServiceTest {

    @Test
    void shouldRouteByProviderAndKeepUsage() throws Exception {
        LlmProviderAdapter adapter = adapter("openai_compatible");
        RuntimeLlmGatewayService gateway = gateway(List.of(adapter));

        LlmCompletionResult result = gateway.chatCompletionResult("system", "user", model("openai_compatible"));

        assertThat(result.content()).isEqualTo("answer");
        assertThat(result.totalTokens()).isEqualTo(15);
    }

    @Test
    void shouldRejectUnsupportedProviderExplicitly() {
        RuntimeLlmGatewayService gateway = gateway(List.of(adapter("openai")));

        assertThatThrownBy(() -> gateway.chatCompletion("system", "user", model("anthropic")))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("未找到模型供应商适配器");
    }

    @Test
    void shouldForwardNativeStreamingDeltas() throws Exception {
        LlmProviderAdapter adapter = streamingAdapter("minimax");
        RuntimeLlmGatewayService gateway = gateway(List.of(adapter));
        List<String> deltas = new ArrayList<>();

        String content = gateway.chatCompletionStreaming("system", "user", model("minimax"), deltas::add);

        assertThat(content).isEqualTo("流式回答");
        assertThat(deltas).containsExactly("流式", "回答");
        assertThat(gateway.supportsStreaming(model("minimax"))).isTrue();
    }

    @Test
    void shouldEnforceSharedRunLlmCallBudget() throws Exception {
        RuntimeLlmGatewayService gateway = gateway(List.of(adapter("openai_compatible")));
        LlmCallBudget budget = new LlmCallBudget(1);
        ExecutionContext.ModelContext model = model("openai_compatible", budget);

        gateway.chatCompletion("system", "first", model);

        assertThatThrownBy(() -> gateway.chatCompletion("system", "second", model))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("LLM 调用次数超过上限 1");
        assertThat(budget.consumedCalls()).isEqualTo(2);
    }

    private LlmProviderAdapter adapter(String provider) {
        return new LlmProviderAdapter() {
            @Override
            public boolean supports(String value) {
                return provider.equals(value);
            }

            @Override
            public LlmCompletionResult chatCompletion(String systemPrompt, String userPrompt,
                                                      ExecutionContext.ModelContext context) {
                return new LlmCompletionResult("<think>x</think>answer", provider,
                        context.modelIdentifier(), 10, 5, 15, 20L);
            }
        };
    }

    private RuntimeLlmGatewayService gateway(List<LlmProviderAdapter> adapters) {
        return new RuntimeLlmGatewayService(adapters, ObservationRegistry.NOOP, new SimpleMeterRegistry());
    }

    private LlmProviderAdapter streamingAdapter(String provider) {
        return new LlmProviderAdapter() {
            @Override
            public boolean supports(String value) {
                return provider.equals(value);
            }

            @Override
            public LlmCompletionResult chatCompletion(String systemPrompt, String userPrompt,
                                                      ExecutionContext.ModelContext context) {
                return new LlmCompletionResult("非流式回答", provider, context.modelIdentifier(), null, null, null, 20L);
            }

            @Override
            public boolean supportsStreaming() {
                return true;
            }

            @Override
            public LlmCompletionResult chatCompletionStreaming(String systemPrompt, String userPrompt,
                                                               ExecutionContext.ModelContext context,
                                                               java.util.function.Consumer<String> consumer) {
                consumer.accept("<thi");
                consumer.accept("nk>内部推理</think>流式");
                consumer.accept("回答");
                return new LlmCompletionResult("<think>内部推理</think>流式回答",
                        provider, context.modelIdentifier(), 10, 5, 15, 20L);
            }
        };
    }

    private ExecutionContext.ModelContext model(String provider) {
        return model(provider, null);
    }

    private ExecutionContext.ModelContext model(String provider, LlmCallBudget budget) {
        return new ExecutionContext.ModelContext(
                "MODEL", "Model", provider, "chat", "http://localhost", "key", null, "model",
                0.2D, 100, 1000, 100, 100, 0.8D, "cl100k_base", 30, "enabled", budget
        );
    }
}
