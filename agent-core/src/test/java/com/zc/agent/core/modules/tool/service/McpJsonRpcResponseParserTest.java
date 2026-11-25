package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpJsonRpcResponseParserTest {

    private final McpJsonRpcResponseParser parser = new McpJsonRpcResponseParser(new ObjectMapper());

    @Test
    void shouldExtractMatchingResponseFromEventStream() {
        String body = """
                event: message
                data: {"jsonrpc":"2.0","method":"notifications/message","params":{}}

                event: message
                id: event-2
                data: {"jsonrpc":"2.0","id":"req-1","result":{"content":[{"type":"text","text":"ok"}]}}

                """;

        JsonNode result = parser.parse(body, "text/event-stream", "req-1", "TEST_TOOL");

        assertThat(result.path("result").path("content").path(0).path("text").asText()).isEqualTo("ok");
    }

    @Test
    void shouldMapJsonRpcErrorToToolExecutionException() {
        String body = """
                {"jsonrpc":"2.0","id":"req-1","error":{"code":-32602,"message":"Invalid params"}}
                """;

        assertThatThrownBy(() -> parser.parse(body, "application/json", "req-1", "TEST_TOOL"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Invalid params");
    }

    @Test
    void shouldRejectMismatchedRequestId() {
        String body = """
                {"jsonrpc":"2.0","id":"other","result":{}}
                """;

        assertThatThrownBy(() -> parser.parse(body, "application/json", "req-1", "TEST_TOOL"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("requestId");
    }
}
