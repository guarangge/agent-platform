package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.model.LlmContentPart;
import com.zc.agent.core.modules.runtime.model.LlmImageContentPart;
import com.zc.agent.core.modules.runtime.model.LlmTextContentPart;
import com.zc.agent.core.config.TraceContextHeaderInjector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Base64;

/**
 * OpenAI Chat Completions 及兼容协议适配器。
 */
@Component
public class OpenAiCompatibleLlmProviderAdapter implements LlmProviderAdapter {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("openai", "openai_compatible", "custom", "minimax");

    private final ObjectMapper objectMapper;
    private final TraceContextHeaderInjector traceContextHeaderInjector;

    public OpenAiCompatibleLlmProviderAdapter(ObjectMapper objectMapper,
                                              TraceContextHeaderInjector traceContextHeaderInjector) {
        this.objectMapper = objectMapper;
        this.traceContextHeaderInjector = traceContextHeaderInjector;
    }

    @Override
    public boolean supports(String provider) {
        return provider != null && SUPPORTED_PROVIDERS.contains(provider.trim().toLowerCase());
    }

    @Override
    public LlmCompletionResult chatCompletion(String systemPrompt,
                                              String userPrompt,
                                              ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        return chatCompletion(systemPrompt, List.of(new LlmTextContentPart(userPrompt)), modelContext);
    }

    @Override
    public LlmCompletionResult chatCompletion(String systemPrompt,
                                              List<LlmContentPart> userContent,
                                              ExecutionContext.ModelContext modelContext) throws IOException, InterruptedException {
        int timeoutSeconds = positive(modelContext.timeoutSeconds(), 30);
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", modelContext.modelIdentifier(),
                "temperature", modelContext.temperature() == null ? 0.2D : modelContext.temperature(),
                "max_tokens", positive(modelContext.maxTokens(), 1024),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", toOpenAiContent(userContent))
                )
        ));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(modelContext)))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + modelContext.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        traceContextHeaderInjector.inject(requestBuilder);
        HttpRequest request = requestBuilder.build();
        long startedAt = System.nanoTime();
        HttpResponse<String> response = send(client, request, timeoutSeconds);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            throw new LlmProviderException(
                    "LLM 请求失败，status=" + response.statusCode() + ", body=" + truncate(response.body()),
                    response.statusCode() == 429 ? "LLM_RATE_LIMITED" : "LLM_PROVIDER_ERROR",
                    response.statusCode(),
                    retryable,
                    null
            );
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new LlmProviderException("LLM 返回内容为空", "LLM_EMPTY_RESPONSE", response.statusCode(), false, null);
        }
        JsonNode usage = root.path("usage");
        return new LlmCompletionResult(
                content,
                modelContext.provider(),
                modelContext.modelIdentifier(),
                nullableInt(usage, "prompt_tokens"),
                nullableInt(usage, "completion_tokens"),
                nullableInt(usage, "total_tokens"),
                durationMs
        );
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /** 使用 OpenAI-compatible SSE 协议实时读取正文增量。 */
    @Override
    public LlmCompletionResult chatCompletionStreaming(String systemPrompt,
                                                        String userPrompt,
                                                        ExecutionContext.ModelContext modelContext,
                                                        Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        return chatCompletionStreaming(systemPrompt, List.of(new LlmTextContentPart(userPrompt)), modelContext, deltaConsumer);
    }

    @Override
    public LlmCompletionResult chatCompletionStreaming(String systemPrompt,
                                                        List<LlmContentPart> userContent,
                                                        ExecutionContext.ModelContext modelContext,
                                                        Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        int timeoutSeconds = positive(modelContext.timeoutSeconds(), 30);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelContext.modelIdentifier());
        payload.put("temperature", modelContext.temperature() == null ? 0.2D : modelContext.temperature());
        payload.put("max_tokens", positive(modelContext.maxTokens(), 1024));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", toOpenAiContent(userContent))
        ));
        payload.put("stream", true);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(modelContext)))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + modelContext.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        traceContextHeaderInjector.inject(requestBuilder);
        long startedAt = System.nanoTime();
        HttpResponse<Stream<String>> response = sendStream(client, requestBuilder.build(), timeoutSeconds);
        try (Stream<String> lines = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = lines.collect(Collectors.joining("\n"));
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                throw new LlmProviderException(
                        "LLM 流式请求失败，status=" + response.statusCode() + ", body=" + truncate(errorBody),
                        response.statusCode() == 429 ? "LLM_RATE_LIMITED" : "LLM_PROVIDER_ERROR",
                        response.statusCode(), retryable, null
                );
            }
            StreamingAccumulator accumulator = new StreamingAccumulator();
            lines.forEach(line -> consumeStreamLine(line, accumulator, deltaConsumer));
            if (accumulator.content.isEmpty()) {
                throw new LlmProviderException("LLM 流式返回内容为空", "LLM_EMPTY_RESPONSE", response.statusCode(), false, null);
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            return new LlmCompletionResult(
                    accumulator.content.toString(), modelContext.provider(), modelContext.modelIdentifier(),
                    accumulator.promptTokens, accumulator.completionTokens, accumulator.totalTokens, durationMs
            );
        }
    }

    private Object toOpenAiContent(List<LlmContentPart> contentParts) {
        if (contentParts.size() == 1 && contentParts.get(0) instanceof LlmTextContentPart textPart) {
            return textPart.text();
        }
        return contentParts.stream().map(part -> {
            if (part instanceof LlmTextContentPart textPart) {
                return Map.<String, Object>of("type", "text", "text", textPart.text());
            }
            if (part instanceof LlmImageContentPart imagePart) {
                String dataUrl = "data:" + imagePart.mimeType() + ";base64,"
                        + Base64.getEncoder().encodeToString(imagePart.content());
                return Map.<String, Object>of("type", "image_url", "image_url", Map.of("url", dataUrl));
            }
            throw new IllegalArgumentException("不支持的 LLM 内容片段：" + part.getClass().getName());
        }).toList();
    }

    private void consumeStreamLine(String line,
                                   StreamingAccumulator accumulator,
                                   Consumer<String> deltaConsumer) {
        if (line == null || !line.startsWith("data:")) return;
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return;
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode contentNode = root.path("choices").path(0).path("delta").path("content");
            if (contentNode.isTextual() && !contentNode.asText().isEmpty()) {
                String delta = contentNode.asText();
                accumulator.content.append(delta);
                deltaConsumer.accept(delta);
            }
            JsonNode usage = root.path("usage");
            accumulator.promptTokens = firstIntegral(usage, "prompt_tokens", accumulator.promptTokens);
            accumulator.completionTokens = firstIntegral(usage, "completion_tokens", accumulator.completionTokens);
            accumulator.totalTokens = firstIntegral(usage, "total_tokens", accumulator.totalTokens);
        } catch (IOException exception) {
            throw new IllegalStateException("解析 LLM SSE 数据失败", exception);
        }
    }

    private Integer firstIntegral(JsonNode node, String field, Integer current) {
        Integer value = nullableInt(node, field);
        return value == null ? current : value;
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request, int timeoutSeconds)
            throws IOException, InterruptedException {
        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(timeoutSeconds + 2L, TimeUnit.SECONDS)
                    .get(timeoutSeconds + 3L, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new LlmProviderException("LLM 请求超时，timeoutSeconds=" + timeoutSeconds,
                    "LLM_TIMEOUT", null, true, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            throw new LlmProviderException("LLM 请求失败: " + (cause == null ? exception.getMessage() : cause.getMessage()),
                    "LLM_CONNECTION_ERROR", null, true, exception);
        }
    }

    private HttpResponse<Stream<String>> sendStream(HttpClient client, HttpRequest request, int timeoutSeconds)
            throws IOException, InterruptedException {
        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .orTimeout(timeoutSeconds + 2L, TimeUnit.SECONDS)
                    .get(timeoutSeconds + 3L, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new LlmProviderException("LLM 流式请求超时，timeoutSeconds=" + timeoutSeconds,
                    "LLM_TIMEOUT", null, true, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException interruptedException) throw interruptedException;
            if (cause instanceof IOException ioException) throw ioException;
            throw new LlmProviderException("LLM 流式请求失败: "
                    + (cause == null ? exception.getMessage() : cause.getMessage()),
                    "LLM_CONNECTION_ERROR", null, true, exception);
        }
    }

    private String endpoint(ExecutionContext.ModelContext context) {
        String baseUrl = context.baseUrl().endsWith("/")
                ? context.baseUrl().substring(0, context.baseUrl().length() - 1)
                : context.baseUrl();
        String apiPath = context.apiPath() == null || context.apiPath().isBlank()
                ? "/chat/completions"
                : context.apiPath();
        return baseUrl + (apiPath.startsWith("/") ? apiPath : "/" + apiPath);
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.intValue() : null;
    }

    private int positive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static final class StreamingAccumulator {
        private final StringBuilder content = new StringBuilder();
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
