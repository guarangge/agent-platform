package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStdioTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    private final McpStdioTransport transport = new McpStdioTransport(
            objectMapper,
            new McpJsonRpcResponseParser(objectMapper),
            new EnvironmentPlaceholderResolver(),
            javaCommand
    );

    @AfterEach
    void tearDown() {
        transport.close();
    }

    @Test
    void shouldInitializeDiscoverAndCallStdioServer() {
        RegistryAgentTool tool = tool();

        Map<String, Object> health = transport.testConnectivity(tool);
        McpAlternativeTransport.McpTransportCallResult result = transport.call(tool, Map.of("name", "Codex"));

        assertThat(health).containsEntry("transportType", "stdio");
        assertThat(String.valueOf(health.get("discovery"))).contains("echo");
        assertThat(result.rpcResponse().path("result").path("content").get(0).path("text").asText())
                .isEqualTo("hello Codex");
    }

    @Test
    void shouldRejectCommandOutsideRuntimeAllowlist() {
        McpStdioTransport restricted = new McpStdioTransport(
                objectMapper,
                new McpJsonRpcResponseParser(objectMapper)
        );

        assertThatThrownBy(() -> restricted.testConnectivity(tool("/tmp/npx")))
                .isInstanceOf(com.zc.agent.core.modules.tool.model.ToolExecutionException.class)
                .hasMessageContaining("不在运行白名单");
    }

    private RegistryAgentTool tool() {
        return tool(javaCommand);
    }

    private RegistryAgentTool tool(String command) {
        JsonNode invokeConfig = objectMapper.valueToTree(Map.of(
                "command", command,
                "args", List.of("-cp", System.getProperty("java.class.path"), FakeMcpStdioServer.class.getName())
        ));
        JsonNode empty = JsonNodeFactory.instance.objectNode();
        return new RegistryAgentTool(
                1L, "ECHO", "Echo", "system", "general", empty, empty,
                "mcp", "stdio", "MCP_STDIO", "MCP Stdio", "stdio-test", "stdio-test",
                "test", null, "none", empty, empty, 5, 0, "echo", "mcp_tool",
                invokeConfig, empty, empty, "default", 5, "enabled", "test", empty
        );
    }

    /**
     * 测试用 MCP JSON Lines 子进程。
     */
    public static class FakeMcpStdioServer {
        public static void main(String[] args) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode request = mapper.readTree(line);
                    if (!request.has("id")) continue;
                    String method = request.path("method").asText();
                    Object result;
                    if ("initialize".equals(method)) {
                        result = Map.of(
                                "protocolVersion", "2025-11-25",
                                "serverInfo", Map.of("name", "fake-stdio", "version", "1"),
                                "capabilities", Map.of("tools", Map.of())
                        );
                    } else if ("tools/list".equals(method)) {
                        result = Map.of("tools", List.of(Map.of("name", "echo")));
                    } else {
                        String name = request.path("params").path("arguments").path("name").asText();
                        result = Map.of("content", List.of(Map.of("type", "text", "text", "hello " + name)));
                    }
                    System.out.println(mapper.writeValueAsString(Map.of(
                            "jsonrpc", "2.0",
                            "id", request.path("id").asText(),
                            "result", result
                    )));
                    System.out.flush();
                }
            }
        }
    }
}
