package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.model.LlmContentPart;
import com.zc.agent.core.modules.runtime.model.LlmImageContentPart;
import com.zc.agent.core.modules.runtime.model.LlmTextContentPart;
import com.zc.agent.core.modules.attachment.service.ConversationAttachmentService;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 运行时 LLM 网关，负责供应商路由和响应清洗。
 */
@Service
public class RuntimeLlmGatewayService {

    private final List<LlmProviderAdapter> providerAdapters;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final CostGovernanceService costGovernanceService;
    private final ConversationAttachmentService attachmentService;

    @Autowired
    public RuntimeLlmGatewayService(List<LlmProviderAdapter> providerAdapters,
                                    ObservationRegistry observationRegistry,
                                    MeterRegistry meterRegistry,
                                    CostGovernanceService costGovernanceService,
                                    ConversationAttachmentService attachmentService) {
        this.providerAdapters = providerAdapters.stream()
                .sorted(Comparator.comparing(item -> item.getClass().getName()))
                .toList();
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.costGovernanceService = costGovernanceService;
        this.attachmentService = attachmentService;
    }

    /** 保留原构造契约，供独立网关测试和外部嵌入场景使用。 */
    public RuntimeLlmGatewayService(List<LlmProviderAdapter> providerAdapters,
                                    ObservationRegistry observationRegistry,
                                    MeterRegistry meterRegistry) {
        this(providerAdapters, observationRegistry, meterRegistry, null, null);
    }

    public String chatCompletion(String systemPrompt,
                                 String userPrompt,
                                 ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        return chatCompletionResult(systemPrompt, userPrompt, modelContext).content();
    }

    public LlmCompletionResult chatCompletionResult(String systemPrompt,
                                                    String userPrompt,
                                                    ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        return executeCompletion(systemPrompt, userPrompt, modelContext, null);
    }

    /** 使用供应商原生流式协议生成响应。 */
    public String chatCompletionStreaming(String systemPrompt,
                                          String userPrompt,
                                          ExecutionContext.ModelContext modelContext,
                                          Consumer<String> deltaConsumer) throws IOException, InterruptedException {
        return executeCompletion(systemPrompt, userPrompt, modelContext, deltaConsumer).content();
    }

    /** 使用当前消息附件构造多模态非流式请求。 */
    public String chatCompletion(String systemPrompt,
                                 String userPrompt,
                                 ExecutionContext executionContext,
                                 ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        return executeMultimodalCompletion(systemPrompt, userPrompt, executionContext, modelContext, null).content();
    }

    /** 使用当前消息附件构造多模态流式请求。 */
    public String chatCompletionStreaming(String systemPrompt,
                                          String userPrompt,
                                          ExecutionContext executionContext,
                                          ExecutionContext.ModelContext modelContext,
                                          Consumer<String> deltaConsumer) throws IOException, InterruptedException {
        return executeMultimodalCompletion(systemPrompt, userPrompt, executionContext, modelContext, deltaConsumer).content();
    }

    private LlmCompletionResult executeMultimodalCompletion(String systemPrompt,
                                                            String userPrompt,
                                                            ExecutionContext executionContext,
                                                            ExecutionContext.ModelContext modelContext,
                                                            Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        List<LlmContentPart> parts = buildContentParts(userPrompt, executionContext);
        if (parts.size() == 1) return executeCompletion(systemPrompt, userPrompt, modelContext, deltaConsumer);
        if (!modelContext.supportsInputModality("image")) {
            throw new LlmProviderException("当前回复模型未声明 image 输入能力",
                    "LLM_IMAGE_INPUT_NOT_SUPPORTED", null, false, null);
        }
        int callNo = modelContext.callBudget() == null ? 0 : modelContext.callBudget().consume();
        long projectedInputTokens = Math.max(1, ((systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length()) + 3L) / 4L) + (parts.size() - 1L) * 1000L;
        if (costGovernanceService != null) {
            costGovernanceService.assertAllowed("llm", modelContext.modelCode(), projectedInputTokens,
                    modelContext.maxTokens() == null ? 1024 : modelContext.maxTokens(), java.math.BigDecimal.ONE);
        }
        String provider = safeTag(modelContext.provider());
        Observation observation = Observation.createNotStarted("agent.llm.call", observationRegistry)
                .lowCardinalityKeyValue("gen_ai.provider.name", provider)
                .highCardinalityKeyValue("gen_ai.request.model", safeTag(modelContext.modelIdentifier()))
                .highCardinalityKeyValue("agent.llm.call_no", String.valueOf(callNo))
                .lowCardinalityKeyValue("gen_ai.input.modality", "text_image").start();
        long startedAt = System.nanoTime();
        try (Observation.Scope ignored = observation.openScope()) {
            LlmProviderAdapter adapter = uniqueAdapter(modelContext);
            StreamingResponseSanitizer streamingSanitizer = deltaConsumer != null && adapter.supportsStreaming()
                    ? new StreamingResponseSanitizer(deltaConsumer) : null;
            LlmCompletionResult result;
            if (streamingSanitizer != null) {
                result = adapter.chatCompletionStreaming(
                        systemPrompt, parts, modelContext, streamingSanitizer::accept);
                streamingSanitizer.finish();
            } else {
                result = adapter.chatCompletion(systemPrompt, parts, modelContext);
            }
            addUsageObservation(observation, result);
            recordTokens(provider, result);
            if (costGovernanceService != null) costGovernanceService.recordLlm(modelContext, result);
            return new LlmCompletionResult(sanitizeResponseText(result.content()), result.provider(), result.modelIdentifier(),
                    result.promptTokens(), result.completionTokens(), result.totalTokens(), result.durationMs());
        } catch (IOException | InterruptedException | RuntimeException exception) {
            observation.error(exception);
            throw exception;
        } finally {
            Timer.builder("agent.llm.request.duration").tag("provider", provider).register(meterRegistry)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            observation.stop();
        }
    }

    private List<LlmContentPart> buildContentParts(String userPrompt, ExecutionContext context) {
        List<LlmContentPart> parts = new java.util.ArrayList<>();
        parts.add(new LlmTextContentPart(userPrompt));
        if (attachmentService == null || context == null || context.messageContext() == null
                || context.messageContext().attachments() == null) return parts;
        for (ExecutionContext.MessageAttachmentContext attachment : context.messageContext().attachments()) {
            var content = attachmentService.readForRuntime(
                    context.conversationContext().conversationId(), attachment.attachmentId());
            parts.add(new LlmImageContentPart(attachment.attachmentId(), content.mimeType(), content.bytes()));
        }
        return parts;
    }

    /** 判断当前模型配置是否存在唯一且支持原生流式的适配器。 */
    public boolean supportsStreaming(ExecutionContext.ModelContext modelContext) {
        return providerAdapters.stream()
                .filter(adapter -> adapter.supports(modelContext.provider()))
                .filter(LlmProviderAdapter::supportsStreaming)
                .count() == 1;
    }

    private LlmCompletionResult executeCompletion(String systemPrompt,
                                                   String userPrompt,
                                                   ExecutionContext.ModelContext modelContext,
                                                   Consumer<String> deltaConsumer) throws IOException, InterruptedException {
        int callNo = modelContext.callBudget() == null ? 0 : modelContext.callBudget().consume();
        long projectedInputTokens = Math.max(1, ((systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length()) + 3L) / 4L);
        if (costGovernanceService != null) {
            costGovernanceService.assertAllowed("llm", modelContext.modelCode(), projectedInputTokens,
                    modelContext.maxTokens() == null ? 1024 : modelContext.maxTokens(), java.math.BigDecimal.ONE);
        }
        String provider = safeTag(modelContext.provider());
        Observation observation = Observation.createNotStarted("agent.llm.call", observationRegistry)
                .lowCardinalityKeyValue("gen_ai.provider.name", provider)
                .highCardinalityKeyValue("gen_ai.request.model", safeTag(modelContext.modelIdentifier()))
                .highCardinalityKeyValue("agent.llm.call_no", String.valueOf(callNo))
                .start();
        long startedAt = System.nanoTime();
        try (Observation.Scope ignored = observation.openScope()) {
            LlmCompletionResult result = invokeProvider(systemPrompt, userPrompt, modelContext, deltaConsumer);
            addUsageObservation(observation, result);
            recordTokens(provider, result);
            if (costGovernanceService != null) {
                costGovernanceService.recordLlm(modelContext, result);
            }
            return result;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            observation.error(exception);
            throw exception;
        } finally {
            Timer.builder("agent.llm.request.duration")
                    .description("LLM provider request duration")
                    .tag("provider", provider)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            observation.stop();
        }
    }

    private LlmCompletionResult invokeProvider(String systemPrompt,
                                                String userPrompt,
                                                ExecutionContext.ModelContext modelContext,
                                                Consumer<String> deltaConsumer) throws IOException, InterruptedException {
        LlmProviderAdapter adapter = uniqueAdapter(modelContext);
        StreamingResponseSanitizer streamingSanitizer = deltaConsumer != null && adapter.supportsStreaming()
                ? new StreamingResponseSanitizer(deltaConsumer) : null;
        LlmCompletionResult result;
        if (streamingSanitizer != null) {
            result = adapter.chatCompletionStreaming(
                    systemPrompt, userPrompt, modelContext, streamingSanitizer::accept);
            streamingSanitizer.finish();
        } else {
            result = adapter.chatCompletion(systemPrompt, userPrompt, modelContext);
        }
        return new LlmCompletionResult(
                sanitizeResponseText(result.content()), result.provider(), result.modelIdentifier(),
                result.promptTokens(), result.completionTokens(), result.totalTokens(), result.durationMs()
        );
    }

    private LlmProviderAdapter uniqueAdapter(ExecutionContext.ModelContext modelContext) throws LlmProviderException {
        List<LlmProviderAdapter> matched = providerAdapters.stream()
                .filter(adapter -> adapter.supports(modelContext.provider()))
                .toList();
        if (matched.isEmpty()) {
            throw new LlmProviderException("未找到模型供应商适配器: " + modelContext.provider(),
                    "LLM_PROVIDER_NOT_SUPPORTED", null, false, null);
        }
        if (matched.size() > 1) {
            throw new LlmProviderException("模型供应商适配器冲突: " + modelContext.provider(),
                    "LLM_PROVIDER_CONFLICT", null, false, null);
        }
        return matched.get(0);
    }

    private void addUsageObservation(Observation observation, LlmCompletionResult result) {
        if (result.promptTokens() != null) observation.highCardinalityKeyValue("gen_ai.usage.input_tokens", String.valueOf(result.promptTokens()));
        if (result.completionTokens() != null) observation.highCardinalityKeyValue("gen_ai.usage.output_tokens", String.valueOf(result.completionTokens()));
    }

    private void recordTokens(String provider, LlmCompletionResult result) {
        incrementTokenCounter(provider, "input", result.promptTokens());
        incrementTokenCounter(provider, "output", result.completionTokens());
    }

    private void incrementTokenCounter(String provider, String type, Integer tokens) {
        if (tokens == null || tokens <= 0) return;
        Counter.builder("agent.llm.tokens")
                .description("LLM token usage")
                .tag("provider", provider)
                .tag("type", type)
                .register(meterRegistry)
                .increment(tokens);
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public String sanitizeJsonText(String responseText) {
        String normalizedText = sanitizeResponseText(responseText);
        if (normalizedText.startsWith("```")) {
            normalizedText = normalizedText.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }
        return normalizedText;
    }

    public String sanitizeResponseText(String responseText) {
        String normalizedText = responseText == null ? "" : responseText.trim();
        return normalizedText.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /** 跨 delta 识别 think 标签，避免原生流在完整响应清洗前泄露模型 reasoning。 */
    private static final class StreamingResponseSanitizer {
        private static final String OPEN_TAG = "<think>";
        private static final String CLOSE_TAG = "</think>";

        private final Consumer<String> consumer;
        private final StringBuilder pending = new StringBuilder();
        private boolean insideThink;

        private StreamingResponseSanitizer(Consumer<String> consumer) {
            this.consumer = consumer;
        }

        private void accept(String delta) {
            if (delta == null || delta.isEmpty()) return;
            pending.append(delta);
            process(false);
        }

        private void finish() {
            process(true);
            pending.setLength(0);
        }

        private void process(boolean finished) {
            while (!pending.isEmpty()) {
                if (insideThink) {
                    int closeIndex = pending.indexOf(CLOSE_TAG);
                    if (closeIndex >= 0) {
                        pending.delete(0, closeIndex + CLOSE_TAG.length());
                        insideThink = false;
                        continue;
                    }
                    if (finished) {
                        pending.setLength(0);
                    } else if (pending.length() > CLOSE_TAG.length()) {
                        pending.delete(0, pending.length() - CLOSE_TAG.length());
                    }
                    return;
                }
                int openIndex = pending.indexOf(OPEN_TAG);
                if (openIndex >= 0) {
                    emit(openIndex);
                    pending.delete(0, OPEN_TAG.length());
                    insideThink = true;
                    continue;
                }
                int retained = finished ? 0 : possibleTagPrefixLength(pending, OPEN_TAG);
                int emitLength = pending.length() - retained;
                if (emitLength > 0) emit(emitLength);
                return;
            }
        }

        private void emit(int length) {
            if (length <= 0) return;
            String value = pending.substring(0, length);
            pending.delete(0, length);
            if (!value.isEmpty()) consumer.accept(value);
        }

        private int possibleTagPrefixLength(StringBuilder value, String tag) {
            int max = Math.min(value.length(), tag.length() - 1);
            for (int length = max; length > 0; length--) {
                if (value.substring(value.length() - length).equals(tag.substring(0, length))) return length;
            }
            return 0;
        }
    }
}
