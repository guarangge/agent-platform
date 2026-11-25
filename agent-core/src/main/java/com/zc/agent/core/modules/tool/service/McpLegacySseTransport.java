package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.TraceContextHeaderInjector;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP legacy HTTP+SSE 传输实现。
 * 通过长期 GET 事件流发现消息端点，再使用 POST 发送 JSON-RPC 请求。
 */
@Component
public class McpLegacySseTransport implements McpAlternativeTransport {

    private static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;
    private final McpJsonRpcResponseParser responseParser;
    private final TraceContextHeaderInjector traceContextHeaderInjector;
    private final Map<String, SseSession> sessions = new ConcurrentHashMap<>();

    public McpLegacySseTransport(ObjectMapper objectMapper,
                                 McpJsonRpcResponseParser responseParser,
                                 TraceContextHeaderInjector traceContextHeaderInjector) {
        this.objectMapper = objectMapper;
        this.responseParser = responseParser;
        this.traceContextHeaderInjector = traceContextHeaderInjector;
    }

    @Override
    public boolean supports(String transportType) {
        return "sse".equalsIgnoreCase(transportType) || "legacy_sse".equalsIgnoreCase(transportType);
    }

    @Override
    public McpTransportCallResult call(RegistryAgentTool tool, Map<String, Object> arguments) {
        SseSession session = session(tool);
        String requestId = UUID.randomUUID().toString();
        String raw = session.request(buildRequest(requestId, "tools/call", Map.of(
                "name", operationName(tool),
                "arguments", arguments == null ? Map.of() : arguments
        )), requestId, timeout(tool));
        JsonNode response = responseParser.parse(raw, "application/json", requestId, tool.toolCode());
        return new McpTransportCallResult(response, raw, session.metadata());
    }

    @Override
    public Map<String, Object> testConnectivity(RegistryAgentTool tool) {
        SseSession session = session(tool);
        String requestId = UUID.randomUUID().toString();
        String raw = session.request(buildRequest(requestId, "tools/list", Map.of()), requestId, timeout(tool));
        JsonNode response = responseParser.parse(raw, "application/json", requestId, tool.toolCode());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(session.metadata());
        result.put("providerType", "mcp");
        result.put("transportType", "sse");
        result.put("discovery", Map.of("success", true, "toolNames", toolNames(response)));
        return result;
    }

    private SseSession session(RegistryAgentTool tool) {
        validateEndpoint(tool);
        String key = sessionKey(tool);
        SseSession existing = sessions.get(key);
        if (existing != null && existing.open()) {
            return existing;
        }
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null && existing.open()) {
                return existing;
            }
            closeSession(key);
            SseSession created = open(tool);
            sessions.put(key, created);
            return created;
        }
    }

    private SseSession open(RegistryAgentTool tool) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout(tool)))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(tool.endpoint()))
                .header("Accept", "text/event-stream")
                .GET();
        ToolInvocationSupport.resolveHeaders(tool, objectMapper).forEach(builder::header);
        traceContextHeaderInjector.inject(builder);
        SseSession session = null;
        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw transportError(tool, "MCP legacy SSE 连接失败，status=" + response.statusCode(),
                        "MCP_SSE_CONNECT_STATUS", response.statusCode() >= 500, response.statusCode());
            }
            session = new SseSession(
                    client,
                    URI.create(tool.endpoint()),
                    response.body(),
                    ToolInvocationSupport.resolveHeaders(tool, objectMapper),
                    traceContextHeaderInjector,
                    objectMapper,
                    tool.toolCode()
            );
            session.start();
            session.awaitMessageEndpoint(timeout(tool));
            String requestId = UUID.randomUUID().toString();
            String raw = session.request(buildRequest(requestId, "initialize", Map.of(
                    "protocolVersion", LATEST_PROTOCOL_VERSION,
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "agent-core", "version", "1.0.0")
            )), requestId, timeout(tool));
            JsonNode initializeResponse = responseParser.parse(raw, "application/json", requestId, tool.toolCode());
            session.initialize(initializeResponse.path("result"));
            session.notify(buildNotification("notifications/initialized", Map.of()), timeout(tool));
            return session;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transportError(tool, "MCP legacy SSE 连接被中断", "TOOL_INTERRUPTED", true, null);
        } catch (IOException exception) {
            throw transportError(tool, "MCP legacy SSE 连接失败: " + exception.getMessage(), "MCP_SSE_CONNECT_IO", true, null);
        } catch (RuntimeException exception) {
            if (session != null) session.close();
            throw exception;
        }
    }

    private String buildRequest(String id, String method, Map<String, Object> params) {
        return json(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    }

    private String buildNotification(String method, Map<String, Object> params) {
        return json(Map.of("jsonrpc", "2.0", "method", method, "params", params));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MCP SSE 请求序列化失败", exception);
        }
    }

    private List<String> toolNames(JsonNode response) {
        List<String> names = new ArrayList<>();
        response.path("result").path("tools").forEach(item -> {
            String name = item.path("name").asText("");
            if (!name.isBlank()) names.add(name);
        });
        return names;
    }

    private void validateEndpoint(RegistryAgentTool tool) {
        if (tool.endpoint() == null || tool.endpoint().isBlank()) {
            throw transportError(tool, "MCP legacy SSE 未配置 endpoint", "MCP_ENDPOINT_MISSING", false, null);
        }
    }

    private int timeout(RegistryAgentTool tool) {
        return tool.providerTimeoutSeconds() != null && tool.providerTimeoutSeconds() > 0
                ? tool.providerTimeoutSeconds()
                : tool.timeoutSeconds() != null && tool.timeoutSeconds() > 0 ? tool.timeoutSeconds() : 30;
    }

    private String operationName(RegistryAgentTool tool) {
        return tool.operationName() == null || tool.operationName().isBlank() ? tool.toolCode() : tool.operationName();
    }

    private String sessionKey(RegistryAgentTool tool) {
        return (tool.providerInstanceCode() == null ? tool.toolCode() : tool.providerInstanceCode()) + "@" + tool.endpoint();
    }

    private ToolExecutionException transportError(RegistryAgentTool tool,
                                                   String message,
                                                   String code,
                                                   boolean retryable,
                                                   Integer statusCode) {
        return new ToolExecutionException(message, code, "provider_protocol", retryable, statusCode,
                Map.of("toolCode", tool.toolCode(), "transportType", "sse"));
    }

    private void closeSession(String key) {
        SseSession session = sessions.remove(key);
        if (session != null) session.close();
    }

    @PreDestroy
    public void close() {
        sessions.values().forEach(SseSession::close);
        sessions.clear();
    }

    private static final class SseSession {
        private final HttpClient client;
        private final URI streamUri;
        private final InputStream stream;
        private final Map<String, String> headers;
        private final TraceContextHeaderInjector traceContextHeaderInjector;
        private final ObjectMapper objectMapper;
        private final String toolCode;
        private final CompletableFuture<URI> endpointFuture = new CompletableFuture<>();
        private final Map<String, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
        private volatile boolean open = true;
        private URI messageUri;
        private LocalDateTime initializedAt;
        private String protocolVersion = "";
        private String serverInfo = "{}";
        private String capabilities = "{}";

        private SseSession(HttpClient client,
                           URI streamUri,
                           InputStream stream,
                           Map<String, String> headers,
                           TraceContextHeaderInjector traceContextHeaderInjector,
                           ObjectMapper objectMapper,
                           String toolCode) {
            this.client = client;
            this.streamUri = streamUri;
            this.stream = stream;
            this.headers = headers;
            this.traceContextHeaderInjector = traceContextHeaderInjector;
            this.objectMapper = objectMapper;
            this.toolCode = toolCode;
        }

        private void start() {
            Thread readerThread = new Thread(this::readEvents, "mcp-legacy-sse-" + toolCode);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void awaitMessageEndpoint(int timeoutSeconds) {
            try {
                messageUri = endpointFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                close();
                throw error("MCP legacy SSE 未收到 endpoint 事件", "MCP_SSE_ENDPOINT_TIMEOUT", true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw error("MCP legacy SSE endpoint 等待被中断", "TOOL_INTERRUPTED", true);
            } catch (Exception exception) {
                close();
                throw error("MCP legacy SSE endpoint 解析失败: " + exception.getMessage(), "MCP_SSE_ENDPOINT_INVALID", false);
            }
        }

        private String request(String payload, String requestId, int timeoutSeconds) {
            ensureOpen();
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            pendingResponses.put(requestId, responseFuture);
            try {
                post(payload, timeoutSeconds);
                return responseFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                throw error("MCP legacy SSE 响应超时", "MCP_SSE_RESPONSE_TIMEOUT", true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw error("MCP legacy SSE 调用被中断", "TOOL_INTERRUPTED", true);
            } catch (ToolExecutionException exception) {
                throw exception;
            } catch (Exception exception) {
                throw error("MCP legacy SSE 调用失败: " + exception.getMessage(), "MCP_SSE_IO", true);
            } finally {
                pendingResponses.remove(requestId);
            }
        }

        private void notify(String payload, int timeoutSeconds) {
            ensureOpen();
            try {
                post(payload, timeoutSeconds);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw error("MCP legacy SSE 通知失败: " + exception.getMessage(), "MCP_SSE_IO", true);
            }
        }

        private void post(String payload, int timeoutSeconds) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(messageUri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            headers.forEach(builder::header);
            traceContextHeaderInjector.inject(builder);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw error("MCP legacy SSE POST 失败，status=" + response.statusCode(), "MCP_SSE_POST_STATUS", response.statusCode() >= 500);
            }
            if (response.body() != null && !response.body().isBlank()) {
                dispatchJson(response.body());
            }
        }

        private void readEvents() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String event = "message";
                StringBuilder data = new StringBuilder();
                String line;
                while (open && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        dispatchEvent(event, data.toString());
                        event = "message";
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring(5).stripLeading());
                    }
                }
            } catch (Exception exception) {
                failPending(exception);
            } finally {
                open = false;
                if (!endpointFuture.isDone()) endpointFuture.completeExceptionally(new IOException("SSE stream closed"));
            }
        }

        private void dispatchEvent(String event, String data) throws JsonProcessingException {
            if (data.isBlank()) return;
            if ("endpoint".equals(event)) {
                URI endpoint = URI.create(data.trim());
                endpointFuture.complete(endpoint.isAbsolute() ? endpoint : streamUri.resolve(endpoint));
                return;
            }
            dispatchJson(data);
        }

        private void dispatchJson(String data) throws JsonProcessingException {
            JsonNode node = objectMapper.readTree(data);
            String id = node.path("id").asText("");
            CompletableFuture<String> future = pendingResponses.get(id);
            if (future != null) future.complete(node.toString());
        }

        private void initialize(JsonNode result) {
            protocolVersion = result.path("protocolVersion").asText("");
            if (protocolVersion.isBlank()) {
                close();
                throw error("MCP legacy SSE initialize 缺少 protocolVersion", "MCP_PROTOCOL_VERSION_MISSING", false);
            }
            serverInfo = result.path("serverInfo").toString();
            capabilities = result.path("capabilities").toString();
            initializedAt = LocalDateTime.now();
        }

        private Map<String, Object> metadata() {
            return Map.of(
                    "streamEndpoint", streamUri.toString(),
                    "messageEndpoint", messageUri == null ? "" : messageUri.toString(),
                    "initializedAt", initializedAt == null ? "" : initializedAt.toString(),
                    "protocolVersion", protocolVersion,
                    "serverInfo", serverInfo,
                    "capabilities", capabilities
            );
        }

        private void failPending(Exception exception) {
            pendingResponses.values().forEach(future -> future.completeExceptionally(exception));
            pendingResponses.clear();
        }

        private boolean open() {
            return open;
        }

        private void ensureOpen() {
            if (!open) throw error("MCP legacy SSE 连接已关闭", "MCP_SSE_CLOSED", true);
        }

        private ToolExecutionException error(String message, String code, boolean retryable) {
            return new ToolExecutionException(message, code, "provider_protocol", retryable, null,
                    Map.of("toolCode", toolCode, "streamEndpoint", streamUri.toString()));
        }

        private void close() {
            open = false;
            try {
                stream.close();
            } catch (IOException ignored) {
                // 连接关闭时流可能已由 HttpClient 回收。
            }
            failPending(new IOException("MCP legacy SSE session closed"));
        }
    }
}
