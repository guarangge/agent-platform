package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zc.agent.registry.modules.tool.dto.ToolDiscoveryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRemoteDiscoveryServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldDiscoverToolsUsingStreamableHttpProtocol() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (body.contains("tools/list")) {
                response = "{\"jsonrpc\":\"2.0\",\"id\":\"list\",\"result\":{\"tools\":[{\"name\":\"weather\",\"title\":\"天气\",\"description\":\"查询天气\",\"inputSchema\":{\"type\":\"object\"}}]}}";
                String requestId = new ObjectMapper().readTree(body).path("id").asText();
                response = response.replace("\"list\"", "\"" + requestId + "\"");
            } else if (body.contains("initialize\"")) {
                String requestId = new ObjectMapper().readTree(body).path("id").asText();
                response = "{\"jsonrpc\":\"2.0\",\"id\":\"" + requestId + "\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}";
                exchange.getResponseHeaders().add("Mcp-Session-Id", "session-1");
            } else {
                response = "{}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRemoteDiscoveryService service = new ToolRemoteDiscoveryService(objectMapper);

        var tools = service.discover(new ToolDiscoveryRequest(null, "mcp", "streamable_http", endpoint,
                objectMapper.createObjectNode(), objectMapper.createObjectNode(), objectMapper.createObjectNode()));

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("weather");
        assertThat(tools.get(0).displayName()).isEqualTo("天气");
    }

    @Test
    void shouldRejectStdioCommandPathOutsideDiscoveryAllowlist() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRemoteDiscoveryService service = new ToolRemoteDiscoveryService(objectMapper);
        var invokeConfig = objectMapper.createObjectNode();
        invokeConfig.put("command", "/tmp/npx");
        invokeConfig.putArray("args");

        assertThatThrownBy(() -> service.discover(new ToolDiscoveryRequest(
                null, "mcp", "stdio", null, objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), invokeConfig)))
                .isInstanceOf(com.zc.agent.registry.common.exception.BusinessException.class)
                .hasMessageContaining("不在发现白名单");
    }
}
