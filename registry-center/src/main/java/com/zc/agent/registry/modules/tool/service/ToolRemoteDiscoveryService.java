package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.tool.dto.ToolDiscoveryRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.Set;

/** 兼容 MCP 与外部 HTTP 的远程 Tool 发现客户端。 */
@Service
public class ToolRemoteDiscoveryService {

    private final ObjectMapper objectMapper;
    private final RegistryEnvironmentPlaceholderResolver placeholderResolver;

    @Value("${registry.tools.discovery.allowed-stdio-commands:npx,node,uvx,docker}")
    private String allowedStdioCommands = "npx,node,uvx,docker";

    public ToolRemoteDiscoveryService(ObjectMapper objectMapper) {
        this(objectMapper, new RegistryEnvironmentPlaceholderResolver());
    }

    @Autowired
    public ToolRemoteDiscoveryService(ObjectMapper objectMapper,
                                      RegistryEnvironmentPlaceholderResolver placeholderResolver) {
        this.objectMapper = objectMapper;
        this.placeholderResolver = placeholderResolver;
    }

    /** 调用远端发现协议并返回标准化 Tool 描述。 */
    public List<RemoteTool> discover(ToolDiscoveryRequest request) {
        String transport = normalize(request.transportType());
        try {
            JsonNode response = switch (transport) {
                case "stdio" -> discoverStdio(request.invokeConfig());
                case "legacy_sse", "sse" -> discoverLegacySse(request);
                case "external_http", "http_get" -> discoverExternalHttp(request);
                default -> discoverStreamableHttp(request);
            };
            return parseTools(response);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("远程 tools/list 发现失败: " + exception.getMessage());
        }
    }

    private JsonNode discoverStreamableHttp(ToolDiscoveryRequest request) throws Exception {
        requireEndpoint(request.endpoint());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String initializeId = UUID.randomUUID().toString();
        HttpResponse<String> initialize = client.send(requestBuilder(request, request.endpoint())
                .POST(HttpRequest.BodyPublishers.ofString(rpc(initializeId, "initialize", Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "agent-registry-discovery", "version", "1.0")
                )))).build(), HttpResponse.BodyHandlers.ofString());
        ensureSuccess(initialize);
        String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse(null);
        HttpRequest.Builder initialized = requestBuilder(request, request.endpoint())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                        "jsonrpc", "2.0", "method", "notifications/initialized"))));
        addSession(initialized, sessionId);
        ensureSuccess(client.send(initialized.build(), HttpResponse.BodyHandlers.ofString()));
        String listId = UUID.randomUUID().toString();
        HttpRequest.Builder list = requestBuilder(request, request.endpoint())
                .POST(HttpRequest.BodyPublishers.ofString(rpc(listId, "tools/list", Map.of())));
        addSession(list, sessionId);
        HttpResponse<String> response = client.send(list.build(), HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        return parseRpcBody(response.body());
    }

    private JsonNode discoverLegacySse(ToolDiscoveryRequest request) throws Exception {
        requireEndpoint(request.endpoint());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest.Builder streamRequest = HttpRequest.newBuilder(URI.create(request.endpoint()))
                .timeout(Duration.ofSeconds(15)).header("Accept", "text/event-stream").GET();
        applyHeaders(streamRequest, request.headers());
        applyAuth(streamRequest, request.authConfig());
        HttpResponse<java.util.stream.Stream<String>> stream = client.send(streamRequest.build(), HttpResponse.BodyHandlers.ofLines());
        String messageEndpoint = stream.body()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .filter(line -> line.startsWith("http") || line.startsWith("/"))
                .findFirst().orElseThrow(() -> new BusinessException("legacy SSE 未返回 message endpoint"));
        URI base = URI.create(request.endpoint());
        String resolved = messageEndpoint.startsWith("http") ? messageEndpoint : base.resolve(messageEndpoint).toString();
        ToolDiscoveryRequest httpRequest = new ToolDiscoveryRequest(request.catalogId(), request.providerType(),
                "streamable_http", resolved, request.authConfig(), request.headers(), request.invokeConfig());
        return discoverStreamableHttp(httpRequest);
    }

    private JsonNode discoverStdio(JsonNode invokeConfig) throws Exception {
        JsonNode commandNode = invokeConfig == null ? null : invokeConfig.path("command");
        List<String> command = new ArrayList<>();
        if (commandNode != null && commandNode.isArray()) {
            commandNode.forEach(item -> command.add(item.asText()));
        } else if (commandNode != null && commandNode.isTextual()) {
            command.add(commandNode.asText());
            JsonNode args = invokeConfig.path("args");
            if (args.isArray()) {
                args.forEach(item -> command.add(item.asText()));
            }
        }
        if (command.isEmpty()) {
            throw new BusinessException("stdio 发现必须配置 invokeConfig.command");
        }
        command.replaceAll(placeholderResolver::resolve);
        validateStdioCommand(command.get(0));
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        JsonNode environment = invokeConfig.path("environment");
        if (environment.isObject()) {
            environment.fields().forEachRemaining(entry -> processBuilder.environment().put(
                    entry.getKey(), placeholderResolver.resolve(entry.getValue().asText())));
        }
        Process process = processBuilder.start();
        try {
            String initId = UUID.randomUUID().toString();
            writeLine(process, rpc(initId, "initialize", Map.of(
                    "protocolVersion", "2025-03-26", "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "agent-registry-discovery", "version", "1.0"))));
            readRpcLine(process, initId);
            writeLine(process, objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "method", "notifications/initialized")));
            String listId = UUID.randomUUID().toString();
            writeLine(process, rpc(listId, "tools/list", Map.of()));
            return readRpcLine(process, listId);
        } finally {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    private JsonNode discoverExternalHttp(ToolDiscoveryRequest request) throws Exception {
        requireEndpoint(request.endpoint());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.endpoint()))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET();
        applyHeaders(builder, request.headers());
        applyAuth(builder, request.authConfig());
        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        JsonNode root = objectMapper.readTree(response.body());
        if (root.isArray()) {
            return objectMapper.createObjectNode().set("tools", root);
        }
        return root;
    }

    private List<RemoteTool> parseTools(JsonNode root) {
        JsonNode tools = root.path("result").path("tools");
        if (!tools.isArray()) {
            tools = root.path("tools");
        }
        if (!tools.isArray()) {
            throw new BusinessException("发现响应中不存在 tools 数组");
        }
        List<RemoteTool> result = new ArrayList<>();
        for (JsonNode item : tools) {
            String name = item.path("name").asText();
            if (!name.isBlank()) {
                result.add(new RemoteTool(name, item.path("title").asText(name),
                        item.path("description").asText(""), item.path("inputSchema"),
                        item.path("version").asText(null), item));
            }
        }
        return result;
    }

    private HttpRequest.Builder requestBuilder(ToolDiscoveryRequest request, String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-03-26");
        applyHeaders(builder, request.headers());
        applyAuth(builder, request.authConfig());
        return builder;
    }

    private void applyHeaders(HttpRequest.Builder builder, JsonNode headers) {
        if (headers != null && headers.isObject()) {
            headers.fields().forEachRemaining(entry -> builder.header(
                    entry.getKey(), placeholderResolver.resolve(entry.getValue().asText())));
        }
    }

    private void applyAuth(HttpRequest.Builder builder, JsonNode authConfig) {
        if (authConfig == null || !authConfig.isObject()) {
            return;
        }
        String bearer = authConfig.path("bearerToken").asText(null);
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + placeholderResolver.resolve(bearer));
            return;
        }
        String username = authConfig.path("username").asText(null);
        String password = placeholderResolver.resolve(authConfig.path("password").asText(""));
        if (username != null && !username.isBlank()) {
            String value = Base64.getEncoder().encodeToString(
                    (placeholderResolver.resolve(username) + ':' + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + value);
        }
    }

    private void addSession(HttpRequest.Builder builder, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Mcp-Session-Id", sessionId);
        }
    }

    private String rpc(String id, String method, Map<String, Object> params) throws Exception {
        return objectMapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    }

    private JsonNode parseRpcBody(String body) throws Exception {
        if (body != null && body.contains("data:")) {
            for (String line : body.split("\\R")) {
                if (line.startsWith("data:")) {
                    return objectMapper.readTree(line.substring(5).trim());
                }
            }
        }
        return objectMapper.readTree(body);
    }

    private void writeLine(Process process, String line) throws Exception {
        process.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
    }

    private JsonNode readRpcLine(Process process, String expectedId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                try {
                    JsonNode node = objectMapper.readTree(line);
                    if (expectedId.equals(node.path("id").asText())) {
                        return node;
                    }
                } catch (Exception ignored) {
                    // MCP Server 的普通日志不是 JSON-RPC 响应，继续读取下一行。
                }
            } else {
                Thread.sleep(20);
            }
        }
        throw new BusinessException("stdio tools/list 超时");
    }

    private void ensureSuccess(HttpResponse<?> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("远程发现响应状态异常: " + response.statusCode());
        }
    }

    private void requireEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException("远程发现 endpoint 不能为空");
        }
    }

    private void validateStdioCommand(String command) {
        Set<String> allowed = java.util.Arrays.stream(allowedStdioCommands.split(","))
                .map(String::trim).filter(item -> !item.isBlank()).collect(java.util.stream.Collectors.toSet());
        if (!allowed.contains(command)) {
            throw new BusinessException("MCP stdio 命令不在发现白名单中：" + command);
        }
    }

    private String normalize(String value) {
        return value == null ? "streamable_http" : value.trim().toLowerCase();
    }

    public record RemoteTool(String name, String displayName, String description, JsonNode inputSchema,
                             String version, JsonNode metadata) {
    }
}
