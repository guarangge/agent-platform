package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.config.TraceContextHeaderInjector;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部 HTTP Tool Provider 适配器。
 */
@Component
public class ExternalHttpToolProviderAdapter implements ToolProviderAdapter {

    private final ObjectMapper objectMapper;
    private final TraceContextHeaderInjector traceContextHeaderInjector;

    public ExternalHttpToolProviderAdapter(ObjectMapper objectMapper,
                                           TraceContextHeaderInjector traceContextHeaderInjector) {
        this.objectMapper = objectMapper;
        this.traceContextHeaderInjector = traceContextHeaderInjector;
    }

    @Override
    public boolean supports(String providerType) {
        return "external_http".equals(providerType);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        if (tool.endpoint() == null || tool.endpoint().isBlank()) {
            throw new ToolExecutionException(
                    "外部 Tool 未配置 endpoint",
                    "TOOL_ENDPOINT_MISSING",
                    "configuration",
                    false,
                    null,
                    Map.of("toolCode", tool.toolCode())
            );
        }
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        JsonNode invokeConfig = tool.invokeConfig();
        String path = invokeConfig == null ? null : invokeConfig.path("path").asText(null);
        String method = invokeConfig == null ? "POST" : invokeConfig.path("method").asText("POST");
        String requestPlacement = invokeConfig == null ? "body" : invokeConfig.path("requestPlacement").asText("body");
        String transportType = normalizeTransport(tool.transportType());
        String url = buildUrl(tool.endpoint(), path, requestPlacement, arguments);
        HttpRequest.BodyPublisher bodyPublisher = "query".equalsIgnoreCase(requestPlacement) || "sse".equals(transportType)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(ToolInvocationSupport.resolveRequestBody(tool, arguments, request, objectMapper));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(resolveTimeout(tool)))
                .method("sse".equals(transportType) ? "GET" : method.toUpperCase(), bodyPublisher);
        Map<String, String> headers = new LinkedHashMap<>(ToolInvocationSupport.resolveHeaders(tool, objectMapper));
        headers.putIfAbsent("Content-Type", "application/json");
        if ("sse".equals(transportType)) {
            headers.put("Accept", "text/event-stream");
        }
        headers.forEach(builder::header);
        traceContextHeaderInjector.inject(builder);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .build();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ToolExecutionException(
                        "外部 Tool 调用失败，status=" + response.statusCode(),
                        "EXTERNAL_HTTP_STATUS",
                        "provider_http",
                        response.statusCode() >= 500,
                        response.statusCode(),
                        Map.of("toolCode", tool.toolCode(), "responseBody", response.body() == null ? "" : response.body())
                );
            }
            String responseText = resolveResponseText(response.body(), tool, transportType);
            return new ToolExecutionResult(
                    tool.toolCode(),
                    tool.toolName(),
                    "已调用外部 HTTP Tool[" + tool.toolCode() + "]",
                    responseText,
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("外部 Tool 调用被中断", "TOOL_INTERRUPTED", "system", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (HttpTimeoutException exception) {
            throw new ToolExecutionException("外部 Tool 调用超时", "EXTERNAL_HTTP_TIMEOUT", "timeout", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (IOException exception) {
            throw new ToolExecutionException(
                    "外部 Tool 调用失败: " + exception.getMessage(),
                    "EXTERNAL_HTTP_IO",
                    "network",
                    true,
                    null,
                    Map.of("toolCode", tool.toolCode())
            );
        }
    }

    private String buildUrl(String endpoint, String path, String requestPlacement, Map<String, Object> arguments) {
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String normalizedPath = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (normalizedPath.contains(placeholder)) {
                normalizedPath = normalizedPath.replace(placeholder, URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
        }
        String url = normalizedEndpoint + normalizedPath;
        if (!"query".equalsIgnoreCase(requestPlacement) || arguments.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String resolveResponseText(String responseBody, RegistryAgentTool tool, String transportType) {
        if ("sse".equals(transportType)) {
            return parseSseResponse(responseBody);
        }
        String pointer = tool.outputMapping() == null ? null : tool.outputMapping().path("responseTextPath").asText(null);
        if (pointer != null && !pointer.isBlank()) {
            return ToolInvocationSupport.readJsonText(objectMapper, responseBody, pointer, responseBody);
        }
        return responseBody;
    }

    private int resolveTimeout(RegistryAgentTool tool) {
        if (tool.providerTimeoutSeconds() != null && tool.providerTimeoutSeconds() > 0) {
            return tool.providerTimeoutSeconds();
        }
        return tool.timeoutSeconds() == null || tool.timeoutSeconds() <= 0 ? 30 : tool.timeoutSeconds();
    }

    private String normalizeTransport(String transportType) {
        if (transportType == null || transportType.isBlank()) {
            return "http_post";
        }
        return transportType.trim().toLowerCase();
    }

    private String parseSseResponse(String responseBody) {
        StringBuilder builder = new StringBuilder();
        for (String line : responseBody.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(payload);
        }
        return builder.length() == 0 ? responseBody : builder.toString();
    }
}
