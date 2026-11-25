package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltinToolExecutorsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCalculateDecimalExpressionWithoutBinaryFloatingPointError() {
        CalculatorToolExecutor executor = new CalculatorToolExecutor(objectMapper);

        var result = executor.execute(null, tool("CALCULATOR"),
                new ToolInvocationRequest("", Map.of("expression", "(0.1 + 0.2) * 10")));

        assertThat(result.responseText()).isEqualTo("3");
        assertThat(result.outputSnapshot()).contains("\"result\":\"3\"");
    }

    @Test
    void shouldRejectInvalidCalculatorExpression() {
        CalculatorToolExecutor executor = new CalculatorToolExecutor(objectMapper);

        assertThatThrownBy(() -> executor.execute(null, tool("CALCULATOR"),
                new ToolInvocationRequest("", Map.of("expression", "1 + process.exit()"))))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo("CALCULATOR_EXPRESSION_INVALID");
    }

    @Test
    void shouldParsePlainTextDocument() {
        DocumentParseToolExecutor executor = new DocumentParseToolExecutor(objectMapper);
        String base64 = Base64.getEncoder().encodeToString("通用 Agent 文档解析".getBytes(StandardCharsets.UTF_8));
        try {
            var result = executor.execute(null, tool("DOCUMENT_PARSE"), new ToolInvocationRequest("", Map.of(
                    "fileName", "sample.txt", "contentBase64", base64, "maxChars", 1000)));

            assertThat(result.responseText()).contains("通用 Agent 文档解析");
            assertThat(result.outputSnapshot()).contains("text/plain");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldBlockPrivateHttpAddress() {
        SafeHttpClient client = new SafeHttpClient();

        assertThatThrownBy(() -> client.get(tool("HTTP_FETCH"), "http://127.0.0.1/internal", Map.of(), 1024))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo("HTTP_PRIVATE_ADDRESS_BLOCKED");
    }

    private RegistryAgentTool tool(String code) {
        var empty = JsonNodeFactory.instance.objectNode();
        return new RegistryAgentTool(1L, code, code, "system", "platform_default", empty, empty,
                "internal", "in_process", "internal", "internal", "default", "default",
                "default", null, "none", empty, empty, 30, 0, code.toLowerCase(), "internal_method",
                empty, empty, empty, "default", 30, "enabled", code, empty);
    }
}
