package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Tool Provider 适配器。
 * Streamable HTTP 由本类直接维护，stdio 与 legacy SSE 通过独立 transport 扩展执行。
 */
@Component
public class McpToolProviderAdapter implements ToolProviderAdapter {

    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";
    private static final String ACCEPT_TYPES = "application/json, text/event-stream";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"
    );

    private final ObjectMapper objectMapper;
    private final McpJsonRpcResponseParser responseParser;
    private final TraceContextHeaderInjector traceContextHeaderInjector;
    private final List<McpAlternativeTransport> alternativeTransports;
    private final Map<String, McpSessionState> sessionStateMap = new ConcurrentHashMap<>();

    public McpToolProviderAdapter(ObjectMapper objectMapper,
                                  McpJsonRpcResponseParser responseParser,
                                  TraceContextHeaderInjector traceContextHeaderInjector,
                                  List<McpAlternativeTransport> alternativeTransports) {
        this.objectMapper = objectMapper;
        this.responseParser = responseParser;
        this.traceContextHeaderInjector = traceContextHeaderInjector;
        this.alternativeTransports = alternativeTransports.stream()
                .sorted(Comparator.comparing(item -> item.getClass().getName()))
                .toList();
    }

    @Override
    public boolean supports(String providerType) {
        return "mcp".equals(providerType);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        validateTransport(tool);
        String transport = normalizeTransport(tool.transportType());
        McpAlternativeTransport alternativeTransport = resolveAlternativeTransport(transport);
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        if (alternativeTransport != null) {
            return buildAlternativeResult(tool, alternativeTransport.call(tool, arguments));
        }
        if (tool.endpoint() == null || tool.endpoint().isBlank()) {
            throw new ToolExecutionException("MCP Tool 未配置 endpoint", "MCP_ENDPOINT_MISSING", "configuration", false, null, Map.of("toolCode", tool.toolCode()));
        }
        McpSessionState sessionState = initializeIfNeeded(tool);
        return executeCall(tool, arguments, sessionState, true);
    }

    private ToolExecutionResult buildAlternativeResult(RegistryAgentTool tool,
                                                       McpAlternativeTransport.McpTransportCallResult result) {
        JsonNode rpcResponse = result.rpcResponse();
        if (rpcResponse.path("result").path("isError").asBoolean(false)) {
            throw new ToolExecutionException(
                    "MCP Tool 返回 isError=true: " + resolveMcpResponseText(rpcResponse),
                    "MCP_TOOL_RESULT_ERROR", "provider_execution", false, null,
                    Map.of("toolCode", tool.toolCode(), "transportType", normalizeTransport(tool.transportType()))
            );
        }
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已调用 MCP Tool[" + tool.toolCode() + "]",
                resolveMcpResponseText(rpcResponse),
                result.rawResponse()
        );
    }

    private ToolExecutionResult executeCall(RegistryAgentTool tool,
                                            Map<String, Object> arguments,
                                            McpSessionState sessionState,
                                            boolean allowSessionRecovery) {
        String requestId = UUID.randomUUID().toString();
        String requestBody = buildRequestBody(tool, arguments, requestId);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(tool.endpoint()))
                .timeout(Duration.ofSeconds(resolveTimeout(tool)))
                .header("Content-Type", "application/json")
                .header("Accept", ACCEPT_TYPES)
                .header("MCP-Protocol-Version", sessionState.protocolVersion())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        if (sessionState.sessionId() != null && !sessionState.sessionId().isBlank()) {
            builder.header("Mcp-Session-Id", sessionState.sessionId());
        }
        ToolInvocationSupport.resolveHeaders(tool, objectMapper).forEach(builder::header);
        traceContextHeaderInjector.inject(builder);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .build();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && allowSessionRecovery && sessionState.sessionId() != null) {
                invalidateSession(tool);
                return executeCall(tool, arguments, initializeIfNeeded(tool), false);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ToolExecutionException(
                        "MCP Tool 调用失败，status=" + response.statusCode(),
                        "MCP_CALL_STATUS",
                        "provider_protocol",
                        response.statusCode() >= 500,
                        response.statusCode(),
                        Map.of("toolCode", tool.toolCode(), "responseBody", response.body() == null ? "" : response.body())
                );
            }
            JsonNode rpcResponse = responseParser.parse(
                    response.body(), response.headers().firstValue("Content-Type").orElse(""), requestId, tool.toolCode()
            );
            if (rpcResponse.path("result").path("isError").asBoolean(false)) {
                throw new ToolExecutionException(
                        "MCP Tool 返回 isError=true: " + resolveMcpResponseText(rpcResponse),
                        "MCP_TOOL_RESULT_ERROR", "provider_execution", false, null,
                        Map.of("toolCode", tool.toolCode(), "requestId", requestId)
                );
            }
            String responseText = resolveMcpResponseText(rpcResponse);
            return new ToolExecutionResult(
                    tool.toolCode(),
                    tool.toolName(),
                    "已调用 MCP Tool[" + tool.toolCode() + "]",
                    responseText,
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("MCP Tool 调用被中断", "TOOL_INTERRUPTED", "system", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (HttpTimeoutException exception) {
            throw new ToolExecutionException("MCP Tool 调用超时", "MCP_TIMEOUT", "timeout", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (IOException exception) {
            throw new ToolExecutionException("MCP Tool 调用失败: " + exception.getMessage(), "MCP_IO", "network", true, null, Map.of("toolCode", tool.toolCode()));
        }
    }

    private String buildRequestBody(RegistryAgentTool tool, Map<String, Object> arguments, String requestId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", tool.operationName() == null || tool.operationName().isBlank() ? tool.toolCode() : tool.operationName());
        params.put("arguments", arguments);
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "id", requestId,
                "method", "tools/call",
                "params", params
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ToolExecutionException("MCP Tool 请求体序列化失败", "MCP_REQUEST_SERIALIZE_FAILED", "configuration", false, null, Map.of("toolCode", tool.toolCode()));
        }
    }

    private String resolveMcpResponseText(JsonNode root) {
        try {
            JsonNode contentNode = root.path("result").path("content");
            if (contentNode.isArray() && !contentNode.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode item : contentNode) {
                    String text = item.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        if (builder.length() > 0) {
                            builder.append("\n");
                        }
                        builder.append(text);
                    }
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
            JsonNode structuredContent = root.path("result").path("structuredContent");
            if (!structuredContent.isMissingNode() && !structuredContent.isNull()) {
                return structuredContent.isValueNode() ? structuredContent.asText() : objectMapper.writeValueAsString(structuredContent);
            }
            return objectMapper.writeValueAsString(root.path("result"));
        } catch (Exception exception) {
            return root.toString();
        }
    }

    private int resolveTimeout(RegistryAgentTool tool) {
        if (tool.providerTimeoutSeconds() != null && tool.providerTimeoutSeconds() > 0) {
            return tool.providerTimeoutSeconds();
        }
        return tool.timeoutSeconds() == null || tool.timeoutSeconds() <= 0 ? 30 : tool.timeoutSeconds();
    }

    public Map<String, Object> testConnectivity(RegistryAgentTool tool) {
        validateTransport(tool);
        McpAlternativeTransport alternativeTransport = resolveAlternativeTransport(normalizeTransport(tool.transportType()));
        if (alternativeTransport != null) {
            return alternativeTransport.testConnectivity(tool);
        }
        McpSessionState state = initializeIfNeeded(tool);
        Map<String, Object> discovery = listRemoteTools(tool, state);
        return Map.of(
                "providerType", "mcp",
                "transportType", normalizeTransport(tool.transportType()),
                "endpoint", tool.endpoint() == null ? "" : tool.endpoint(),
                "sessionId", state.sessionId() == null ? "" : state.sessionId(),
                "initializedAt", state.initializedAt() == null ? "" : state.initializedAt().toString(),
                "serverInfo", state.serverInfo(),
                "capabilities", state.capabilities(),
                "protocolVersion", state.protocolVersion(),
                "discovery", discovery
        );
    }

    private McpSessionState initializeIfNeeded(RegistryAgentTool tool) {
        String sessionKey = sessionKey(tool);
        McpSessionState existing = sessionStateMap.get(sessionKey);
        if (existing != null) {
            return existing;
        }
        synchronized (sessionStateMap) {
            McpSessionState cached = sessionStateMap.get(sessionKey);
            if (cached != null) {
                return cached;
            }
            McpSessionState state = doInitialize(tool);
            sessionStateMap.put(sessionKey, state);
            return state;
        }
    }

    private McpSessionState doInitialize(RegistryAgentTool tool) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .build();
            String requestId = UUID.randomUUID().toString();
            String initializeBody = objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestId,
                    "method", "initialize",
                    "params", Map.of(
                            "protocolVersion", LATEST_PROTOCOL_VERSION,
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "agent-core", "version", "1.0.0")
                    )
            ));
            HttpRequest.Builder initializeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tool.endpoint()))
                    .timeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .header("Content-Type", "application/json")
                    .header("Accept", ACCEPT_TYPES)
                    .POST(HttpRequest.BodyPublishers.ofString(initializeBody));
            ToolInvocationSupport.resolveHeaders(tool, objectMapper).forEach(initializeRequest::header);
            traceContextHeaderInjector.inject(initializeRequest);
            HttpResponse<String> initializeResponse = client.send(initializeRequest.build(), HttpResponse.BodyHandlers.ofString());
            if (initializeResponse.statusCode() < 200 || initializeResponse.statusCode() >= 300) {
                throw new ToolExecutionException(
                        "MCP initialize 失败，status=" + initializeResponse.statusCode(),
                        "MCP_INITIALIZE_STATUS",
                        "provider_protocol",
                        initializeResponse.statusCode() >= 500,
                        initializeResponse.statusCode(),
                        Map.of("toolCode", tool.toolCode(), "responseBody", initializeResponse.body() == null ? "" : initializeResponse.body())
                );
            }
            JsonNode initializeRoot = responseParser.parse(
                    initializeResponse.body(), initializeResponse.headers().firstValue("Content-Type").orElse(""),
                    requestId, tool.toolCode()
            );
            JsonNode initializeResult = initializeRoot.path("result");
            String protocolVersion = initializeResult.path("protocolVersion").asText(null);
            if (protocolVersion == null || protocolVersion.isBlank()) {
                throw new ToolExecutionException(
                        "MCP initialize 响应缺少 protocolVersion",
                        "MCP_PROTOCOL_VERSION_MISSING", "provider_protocol", false, null,
                        Map.of("toolCode", tool.toolCode())
                );
            }
            if (!SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersion)) {
                throw new ToolExecutionException(
                        "MCP 服务端返回不支持的协议版本: " + protocolVersion,
                        "MCP_PROTOCOL_VERSION_UNSUPPORTED", "provider_protocol", false, null,
                        Map.of("toolCode", tool.toolCode(), "protocolVersion", protocolVersion)
                );
            }
            if (initializeResult.path("capabilities").path("tools").isMissingNode()) {
                throw new ToolExecutionException(
                        "MCP 服务端未声明 tools capability",
                        "MCP_TOOLS_CAPABILITY_MISSING", "provider_protocol", false, null,
                        Map.of("toolCode", tool.toolCode(), "protocolVersion", protocolVersion)
                );
            }
            String sessionId = initializeResponse.headers().firstValue("Mcp-Session-Id")
                    .or(() -> initializeResponse.headers().firstValue("mcp-session-id"))
                    .orElse(null);
            HttpRequest.Builder initializedRequest = HttpRequest.newBuilder()
                        .uri(URI.create(tool.endpoint()))
                        .timeout(Duration.ofSeconds(resolveTimeout(tool)))
                        .header("Content-Type", "application/json")
                        .header("Accept", ACCEPT_TYPES)
                        .header("MCP-Protocol-Version", protocolVersion)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                                "jsonrpc", "2.0",
                                "method", "notifications/initialized"
                        ))));
            if (sessionId != null && !sessionId.isBlank()) {
                initializedRequest.header("Mcp-Session-Id", sessionId);
            }
            ToolInvocationSupport.resolveHeaders(tool, objectMapper).forEach(initializedRequest::header);
            traceContextHeaderInjector.inject(initializedRequest);
            HttpResponse<String> initializedResponse = client.send(initializedRequest.build(), HttpResponse.BodyHandlers.ofString());
            if (initializedResponse.statusCode() < 200 || initializedResponse.statusCode() >= 300) {
                throw new ToolExecutionException(
                        "MCP initialized 通知失败，status=" + initializedResponse.statusCode(),
                        "MCP_INITIALIZED_STATUS", "provider_protocol", initializedResponse.statusCode() >= 500,
                        initializedResponse.statusCode(), Map.of("toolCode", tool.toolCode())
                );
            }
            String serverInfo = initializeResult.path("serverInfo").isMissingNode()
                    ? ""
                    : initializeResult.path("serverInfo").toString();
            String capabilities = initializeResult.path("capabilities").isMissingNode()
                    ? "{}"
                    : initializeResult.path("capabilities").toString();
            return new McpSessionState(sessionId, LocalDateTime.now(), protocolVersion, serverInfo, capabilities);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("MCP initialize 被中断", "TOOL_INTERRUPTED", "system", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (HttpTimeoutException exception) {
            throw new ToolExecutionException("MCP initialize 超时", "MCP_INITIALIZE_TIMEOUT", "timeout", true, null, Map.of("toolCode", tool.toolCode()));
        } catch (IOException exception) {
            throw new ToolExecutionException("MCP initialize 失败: " + exception.getMessage(), "MCP_INITIALIZE_IO", "network", true, null, Map.of("toolCode", tool.toolCode()));
        }
    }

    private Map<String, Object> listRemoteTools(RegistryAgentTool tool, McpSessionState sessionState) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .build();
            String requestId = UUID.randomUUID().toString();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tool.endpoint()))
                    .timeout(Duration.ofSeconds(resolveTimeout(tool)))
                    .header("Content-Type", "application/json")
                    .header("Accept", ACCEPT_TYPES)
                    .header("MCP-Protocol-Version", sessionState.protocolVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "jsonrpc", "2.0",
                            "id", requestId,
                            "method", "tools/list",
                            "params", Map.of()
                    ))));
            if (sessionState.sessionId() != null && !sessionState.sessionId().isBlank()) {
                requestBuilder.header("Mcp-Session-Id", sessionState.sessionId());
            }
            ToolInvocationSupport.resolveHeaders(tool, objectMapper).forEach(requestBuilder::header);
            traceContextHeaderInjector.inject(requestBuilder);
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Map.of("success", false, "message", "tools/list status=" + response.statusCode());
            }
            JsonNode root = responseParser.parse(
                    response.body(), response.headers().firstValue("Content-Type").orElse(""), requestId, tool.toolCode()
            );
            JsonNode toolsNode = root.path("result").path("tools");
            if (!toolsNode.isArray()) {
                return Map.of("success", true, "toolNames", java.util.List.of());
            }
            java.util.List<String> toolNames = new java.util.ArrayList<>();
            for (JsonNode item : toolsNode) {
                String name = item.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    toolNames.add(name);
                }
            }
            return Map.of("success", true, "toolNames", toolNames);
        } catch (Exception exception) {
            return Map.of("success", false, "message", exception.getMessage() == null ? "tools/list 失败" : exception.getMessage());
        }
    }

    private record McpSessionState(String sessionId,
                                   LocalDateTime initializedAt,
                                   String protocolVersion,
                                   String serverInfo,
                                   String capabilities) {
    }

    private void invalidateSession(RegistryAgentTool tool) {
        sessionStateMap.remove(sessionKey(tool));
    }

    private String sessionKey(RegistryAgentTool tool) {
        return (tool.providerInstanceCode() == null ? "" : tool.providerInstanceCode()) + "@" + tool.endpoint();
    }

    private void validateTransport(RegistryAgentTool tool) {
        String transport = normalizeTransport(tool.transportType());
        if (resolveAlternativeTransport(transport) != null) {
            return;
        }
        if (!"streamable_http".equals(transport) && !"http_post".equals(transport)) {
            throw new ToolExecutionException(
                    "当前 MCP Tool 传输协议暂不支持: " + transport,
                    "MCP_TRANSPORT_UNSUPPORTED",
                    "configuration",
                    false,
                    null,
                    Map.of("toolCode", tool.toolCode(), "transportType", transport)
            );
        }
    }

    private McpAlternativeTransport resolveAlternativeTransport(String transportType) {
        List<McpAlternativeTransport> matched = alternativeTransports.stream()
                .filter(item -> item.supports(transportType))
                .toList();
        if (matched.size() > 1) {
            throw new ToolExecutionException(
                    "MCP transport 扩展冲突: " + transportType,
                    "MCP_TRANSPORT_CONFLICT", "configuration", false, null,
                    Map.of("transportType", transportType, "handlerCount", matched.size())
            );
        }
        return matched.isEmpty() ? null : matched.get(0);
    }

    private String normalizeTransport(String transportType) {
        if (transportType == null || transportType.isBlank()) {
            return "streamable_http";
        }
        return transportType.trim().toLowerCase();
    }
}
