package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zc.agent.core.config.TraceContextHeaderInjector;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpLegacySseTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    private HttpServer server;
    private volatile OutputStream eventStream;
    private McpLegacySseTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/sse", this::handleSse);
        server.createContext("/messages", this::handleMessage);
        server.start();
        transport = new McpLegacySseTransport(
                objectMapper,
                new McpJsonRpcResponseParser(objectMapper),
                mock(TraceContextHeaderInjector.class)
        );
    }

    @AfterEach
    void tearDown() {
        transport.close();
        closeLatch.countDown();
        server.stop(0);
    }

    @Test
    void shouldDiscoverMessageEndpointAndCallTool() {
        RegistryAgentTool tool = tool();

        Map<String, Object> health = transport.testConnectivity(tool);
        McpAlternativeTransport.McpTransportCallResult result = transport.call(tool, Map.of("value", "ok"));

        assertThat(health).containsEntry("transportType", "sse");
        assertThat(String.valueOf(health.get("discovery"))).contains("legacy_echo");
        assertThat(result.rpcResponse().path("result").path("content").get(0).path("text").asText())
                .isEqualTo("legacy ok");
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        eventStream = exchange.getResponseBody();
        writeEvent("endpoint", "/messages");
        try {
            closeLatch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private void handleMessage(HttpExchange exchange) throws IOException {
        JsonNode request = objectMapper.readTree(exchange.getRequestBody());
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
        if (!request.has("id")) return;
        String method = request.path("method").asText();
        Object result;
        if ("initialize".equals(method)) {
            result = Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of("name", "fake-sse", "version", "1"),
                    "capabilities", Map.of("tools", Map.of())
            );
        } else if ("tools/list".equals(method)) {
            result = Map.of("tools", List.of(Map.of("name", "legacy_echo")));
        } else {
            String value = request.path("params").path("arguments").path("value").asText();
            result = Map.of("content", List.of(Map.of("type", "text", "text", "legacy " + value)));
        }
        writeEvent("message", objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", request.path("id").asText(),
                "result", result
        )));
    }

    private synchronized void writeEvent(String event, String data) throws IOException {
        while (eventStream == null) {
            try {
                wait(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("等待 SSE stream 被中断", exception);
            }
        }
        eventStream.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        eventStream.flush();
        notifyAll();
    }

    private RegistryAgentTool tool() {
        JsonNode empty = JsonNodeFactory.instance.objectNode();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sse";
        return new RegistryAgentTool(
                1L, "LEGACY_ECHO", "Legacy Echo", "system", "general", empty, empty,
                "mcp", "sse", "MCP_SSE", "MCP SSE", "sse-test", "sse-test",
                "test", endpoint, "none", empty, empty, 5, 0, "legacy_echo", "mcp_tool",
                empty, empty, empty, "default", 5, "enabled", "test", empty
        );
    }
}
