package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP stdio 传输实现。
 * 每个 Provider 实例维护一个子进程，使用 JSON Lines 通信且不经过 Shell。
 */
@Component
public class McpStdioTransport implements McpAlternativeTransport {

    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";

    private final ObjectMapper objectMapper;
    private final McpJsonRpcResponseParser responseParser;
    private final EnvironmentPlaceholderResolver placeholderResolver;
    private final String allowedStdioCommands;
    private final Map<String, StdioSession> sessions = new ConcurrentHashMap<>();

    public McpStdioTransport(ObjectMapper objectMapper, McpJsonRpcResponseParser responseParser) {
        this(objectMapper, responseParser, new EnvironmentPlaceholderResolver(), "npx,node,uvx,docker");
    }

    @Autowired
    public McpStdioTransport(ObjectMapper objectMapper,
                            McpJsonRpcResponseParser responseParser,
                            EnvironmentPlaceholderResolver placeholderResolver,
                            @Value("${core.tools.mcp.allowed-stdio-commands:npx,node,uvx,docker}")
                            String allowedStdioCommands) {
        this.objectMapper = objectMapper;
        this.responseParser = responseParser;
        this.placeholderResolver = placeholderResolver;
        this.allowedStdioCommands = allowedStdioCommands;
    }

    @Override
    public boolean supports(String transportType) {
        return "stdio".equalsIgnoreCase(transportType);
    }

    @Override
    public McpTransportCallResult call(RegistryAgentTool tool, Map<String, Object> arguments) {
        StdioSession session = session(tool);
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
        StdioSession session = session(tool);
        String requestId = UUID.randomUUID().toString();
        String raw = session.request(buildRequest(requestId, "tools/list", Map.of()), requestId, timeout(tool));
        JsonNode response = responseParser.parse(raw, "application/json", requestId, tool.toolCode());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(session.metadata());
        result.put("providerType", "mcp");
        result.put("transportType", "stdio");
        result.put("discovery", Map.of("success", true, "toolNames", toolNames(response)));
        return result;
    }

    private StdioSession session(RegistryAgentTool tool) {
        String key = sessionKey(tool);
        StdioSession existing = sessions.get(key);
        if (existing != null && existing.alive()) {
            return existing;
        }
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null && existing.alive()) {
                return existing;
            }
            closeSession(key);
            StdioSession created = start(tool);
            sessions.put(key, created);
            return created;
        }
    }

    private StdioSession start(RegistryAgentTool tool) {
        JsonNode config = tool.invokeConfig();
        String command = config == null ? "" : placeholderResolver.resolve(config.path("command").asText("").trim());
        if (command.isBlank()) {
            throw configurationError(tool, "MCP stdio 缺少 invokeConfig.command");
        }
        validateCommand(tool, command);
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        JsonNode args = config.path("args");
        if (args.isArray()) {
            args.forEach(item -> commandLine.add(placeholderResolver.resolve(item.asText())));
        }
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        String workingDirectory = placeholderResolver.resolve(config.path("workingDirectory").asText("").trim());
        if (!workingDirectory.isBlank()) {
            processBuilder.directory(Path.of(workingDirectory).toFile());
        }
        if (!config.path("inheritEnvironment").asBoolean(true)) {
            processBuilder.environment().clear();
        }
        JsonNode environment = config.path("environment");
        if (environment.isObject()) {
            environment.fields().forEachRemaining(entry -> processBuilder.environment().put(
                    entry.getKey(), placeholderResolver.resolve(entry.getValue().asText())));
        }
        StdioSession session = null;
        try {
            Process process = processBuilder.start();
            session = new StdioSession(process, commandLine, objectMapper, responseParser, tool.toolCode());
            String requestId = UUID.randomUUID().toString();
            String raw = session.request(buildRequest(requestId, "initialize", Map.of(
                    "protocolVersion", LATEST_PROTOCOL_VERSION,
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "agent-core", "version", "1.0.0")
            )), requestId, timeout(tool));
            JsonNode response = responseParser.parse(raw, "application/json", requestId, tool.toolCode());
            session.initialize(response.path("result"));
            session.notify(buildNotification("notifications/initialized", Map.of()));
            return session;
        } catch (IOException exception) {
            throw new ToolExecutionException(
                    "MCP stdio 进程启动失败: " + exception.getMessage(), "MCP_STDIO_START_FAILED", "system", false,
                    null, Map.of("toolCode", tool.toolCode(), "command", command)
            );
        } catch (RuntimeException exception) {
            if (session != null) session.close();
            throw exception;
        }
    }

    private void validateCommand(RegistryAgentTool tool, String command) {
        boolean allowed = java.util.Arrays.stream(allowedStdioCommands.split(","))
                .map(String::trim).filter(item -> !item.isBlank()).anyMatch(command::equals);
        if (!allowed) {
            throw configurationError(tool, "MCP stdio 命令不在运行白名单中：" + command);
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
            throw new IllegalStateException("MCP stdio 请求序列化失败", exception);
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

    private int timeout(RegistryAgentTool tool) {
        return tool.providerTimeoutSeconds() != null && tool.providerTimeoutSeconds() > 0
                ? tool.providerTimeoutSeconds()
                : tool.timeoutSeconds() != null && tool.timeoutSeconds() > 0 ? tool.timeoutSeconds() : 30;
    }

    private String operationName(RegistryAgentTool tool) {
        return tool.operationName() == null || tool.operationName().isBlank() ? tool.toolCode() : tool.operationName();
    }

    private String sessionKey(RegistryAgentTool tool) {
        return (tool.providerInstanceCode() == null ? tool.toolCode() : tool.providerInstanceCode()) + "@stdio";
    }

    private ToolExecutionException configurationError(RegistryAgentTool tool, String message) {
        return new ToolExecutionException(message, "MCP_STDIO_CONFIGURATION", "configuration", false, null,
                Map.of("toolCode", tool.toolCode(), "transportType", "stdio"));
    }

    private void closeSession(String key) {
        StdioSession session = sessions.remove(key);
        if (session != null) session.close();
    }

    @PreDestroy
    public void close() {
        sessions.values().forEach(StdioSession::close);
        sessions.clear();
    }

    private static final class StdioSession {
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final ExecutorService readExecutor;
        private final List<String> commandLine;
        private final StringBuilder stderrTail = new StringBuilder();
        private final ObjectMapper objectMapper;
        private final McpJsonRpcResponseParser responseParser;
        private final String toolCode;
        private LocalDateTime initializedAt;
        private String protocolVersion = "";
        private String serverInfo = "{}";
        private String capabilities = "{}";

        private StdioSession(Process process,
                             List<String> commandLine,
                             ObjectMapper objectMapper,
                             McpJsonRpcResponseParser responseParser,
                             String toolCode) {
            this.process = process;
            this.commandLine = List.copyOf(commandLine);
            this.objectMapper = objectMapper;
            this.responseParser = responseParser;
            this.toolCode = toolCode;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.readExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mcp-stdio-response-" + toolCode);
                thread.setDaemon(true);
                return thread;
            });
            Thread stderrReader = new Thread(this::drainStderr, "mcp-stdio-stderr-" + toolCode);
            stderrReader.setDaemon(true);
            stderrReader.start();
        }

        private synchronized String request(String payload, String requestId, int timeoutSeconds) {
            ensureAlive();
            try {
                writeLine(payload);
                Future<String> future = readExecutor.submit(() -> readMatchingResponse(requestId));
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                close();
                throw transportError("MCP stdio 响应超时", "MCP_STDIO_TIMEOUT", true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw transportError("MCP stdio 调用被中断", "TOOL_INTERRUPTED", true);
            } catch (ToolExecutionException exception) {
                throw exception;
            } catch (Exception exception) {
                close();
                throw transportError("MCP stdio 调用失败: " + exception.getMessage(), "MCP_STDIO_IO", true);
            }
        }

        private synchronized void notify(String payload) {
            ensureAlive();
            try {
                writeLine(payload);
            } catch (IOException exception) {
                close();
                throw transportError("MCP stdio 通知发送失败: " + exception.getMessage(), "MCP_STDIO_IO", true);
            }
        }

        private void writeLine(String payload) throws IOException {
            writer.write(payload);
            writer.newLine();
            writer.flush();
        }

        private String readMatchingResponse(String requestId) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                if (requestId.equals(node.path("id").asText())) {
                    responseParser.parse(line, "application/json", requestId, toolCode);
                    return line;
                }
            }
            throw new IOException("MCP stdio stdout 已关闭");
        }

        private void initialize(JsonNode result) {
            protocolVersion = result.path("protocolVersion").asText("");
            if (protocolVersion.isBlank()) {
                close();
                throw transportError("MCP stdio initialize 缺少 protocolVersion", "MCP_PROTOCOL_VERSION_MISSING", false);
            }
            serverInfo = result.path("serverInfo").toString();
            capabilities = result.path("capabilities").toString();
            initializedAt = LocalDateTime.now();
        }

        private Map<String, Object> metadata() {
            return Map.of(
                    "command", commandLine,
                    "processId", process.pid(),
                    "initializedAt", initializedAt == null ? "" : initializedAt.toString(),
                    "protocolVersion", protocolVersion,
                    "serverInfo", serverInfo,
                    "capabilities", capabilities
            );
        }

        private boolean alive() {
            return process.isAlive();
        }

        private void ensureAlive() {
            if (!alive()) {
                throw transportError("MCP stdio 进程已退出", "MCP_STDIO_PROCESS_EXITED", true);
            }
        }

        private void drainStderr() {
            try (BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderr.readLine()) != null) {
                    synchronized (stderrTail) {
                        stderrTail.append(line).append('\n');
                        if (stderrTail.length() > 4000) stderrTail.delete(0, stderrTail.length() - 4000);
                    }
                }
            } catch (IOException ignored) {
                // 进程关闭时 stderr 流会同步关闭。
            }
        }

        private ToolExecutionException transportError(String message, String code, boolean retryable) {
            String stderr;
            synchronized (stderrTail) {
                stderr = stderrTail.toString();
            }
            return new ToolExecutionException(message, code, "provider_protocol", retryable, null,
                    Map.of("toolCode", toolCode, "stderrTail", stderr));
        }

        private void close() {
            readExecutor.shutdownNow();
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
