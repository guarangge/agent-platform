package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zc.agent.core.config.TraceContextHeaderInjector;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.model.LlmImageContentPart;
import com.zc.agent.core.modules.runtime.model.LlmTextContentPart;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleLlmProviderAdapterTest {

    @Test
    void shouldSerializeProviderNeutralImageAsOpenAiContentPart() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"识别完成\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmProviderAdapter adapter = new OpenAiCompatibleLlmProviderAdapter(
                    new ObjectMapper(), mock(TraceContextHeaderInjector.class));

            LlmCompletionResult result = adapter.chatCompletion("system", List.of(
                    new LlmTextContentPart("分析图片"),
                    new LlmImageContentPart(1L, "image/png", new byte[]{1, 2, 3})
            ), model(server.getAddress().getPort()));

            assertThat(requestBody.get()).contains("\"type\":\"text\"", "\"type\":\"image_url\"",
                    "data:image/png;base64,AQID");
            assertThat(result.content()).isEqualTo("识别完成");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseOpenAiCompatibleNativeStream() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            String body = """
                    data: {"choices":[{"delta":{"content":"你好"}}]}

                    data: {"choices":[{"delta":{"content":"，MiniMax"}}]}

                    data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}

                    data: [DONE]

                    """;
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatibleLlmProviderAdapter adapter = new OpenAiCompatibleLlmProviderAdapter(
                    new ObjectMapper(), mock(TraceContextHeaderInjector.class));
            List<String> deltas = new ArrayList<>();

            LlmCompletionResult result = adapter.chatCompletionStreaming(
                    "system", "user", model(server.getAddress().getPort()), deltas::add);

            assertThat(requestBody.get()).contains("\"stream\":true");
            assertThat(deltas).containsExactly("你好", "，MiniMax");
            assertThat(result.content()).isEqualTo("你好，MiniMax");
            assertThat(result.totalTokens()).isEqualTo(14);
        } finally {
            server.stop(0);
        }
    }

    private ExecutionContext.ModelContext model(int port) {
        return new ExecutionContext.ModelContext(
                "MINIMAX_TEST", "MiniMax Test", "minimax", "chat",
                "http://127.0.0.1:" + port, "key", "/v1/chat/completions", "MiniMax-M2.1",
                0.2D, 100, 1000, 100, 100, 0.8D, "cl100k_base", 30, "enabled", null
        );
    }
}
