package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.List;
import com.zc.agent.core.modules.runtime.model.LlmContentPart;
import com.zc.agent.core.modules.runtime.model.LlmTextContentPart;

/**
 * 模型供应商协议适配器。
 */
public interface LlmProviderAdapter {

    boolean supports(String provider);

    LlmCompletionResult chatCompletion(String systemPrompt,
                                       String userPrompt,
                                       ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException;

    /** 多模态协议；未实现的 Provider 仅接受文本片段。 */
    default LlmCompletionResult chatCompletion(String systemPrompt,
                                                List<LlmContentPart> userContent,
                                                ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        if (userContent.stream().anyMatch(part -> !(part instanceof LlmTextContentPart))) {
            throw new LlmProviderException("当前模型供应商适配器不支持图片输入",
                    "LLM_MULTIMODAL_NOT_SUPPORTED", null, false, null);
        }
        String text = userContent.stream().map(part -> ((LlmTextContentPart) part).text())
                .reduce("", (left, right) -> left + right);
        return chatCompletion(systemPrompt, text, modelContext);
    }

    /** 当前适配器是否支持供应商原生流式响应。 */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 使用供应商原生协议生成响应，并在正文增量到达时立即回调。
     * 不支持流式的适配器保留完整响应语义，由上层决定是否回退。
     */
    default LlmCompletionResult chatCompletionStreaming(String systemPrompt,
                                                         String userPrompt,
                                                         ExecutionContext.ModelContext modelContext,
                                                         Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        return chatCompletion(systemPrompt, userPrompt, modelContext);
    }

    /** 多模态流式协议，默认回退对应非流式多模态调用。 */
    default LlmCompletionResult chatCompletionStreaming(String systemPrompt,
                                                         List<LlmContentPart> userContent,
                                                         ExecutionContext.ModelContext modelContext,
                                                         Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        return chatCompletion(systemPrompt, userContent, modelContext);
    }
}
