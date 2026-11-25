package com.zc.agent.sdk.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 JDK HTTP/SSE 传输冻结独立示例与 Runtime Protocol 的宿主契约。 */
class RuntimeSdkExampleContractTest {

    private HttpServer server;
    private final List<RequestSnapshot> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger streamCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/core/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldCreateRunConsumeSseAndResumeApprovalThroughRealHttpTransport() {
        RuntimeSdkExample.ExampleConfig config = new RuntimeSdkExample.ExampleConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/core",
                "contract-token",
                7,
                "Java 契约测试",
                "生成审批后的答案",
                "contract-idempotency-key",
                80,
                true,
                "契约测试批准",
                "审批后完成",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5));

        RuntimeSdkExample.ExampleResult result = RuntimeSdkExample.execute(config);

        assertEquals(31, result.threadId());
        assertEquals("RUN JAVA/1", result.runCode());
        assertEquals("completed", result.status());
        assertEquals(91, result.taskId());
        assertEquals(4, result.lastSequenceNo());
        assertEquals("审批后完成", result.answer());
        assertEquals(2, streamCount.get());

        RequestSnapshot createRun = find("POST", "/api/core/conversations/31/messages/async");
        assertEquals("Bearer contract-token", createRun.authorization());
        assertEquals("contract-idempotency-key", createRun.idempotencyKey());
        assertEquals("80", createRun.priority());
        assertTrue(createRun.body().contains("生成审批后的答案"));

        RequestSnapshot resumedStream = requests.stream()
                .filter(item -> item.path().equals("/api/core/runtime/runs/RUN%20JAVA%2F1/events"))
                .filter(item -> "afterSequence=2".equals(item.query()))
                .findFirst()
                .orElseThrow();
        assertEquals("2", resumedStream.lastEventId());

        RequestSnapshot approval = find("POST", "/api/core/conversations/31/tasks/91/approvals/81/approve");
        assertTrue(approval.body().contains("契约测试批准"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RequestSnapshot(
                exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), body,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("X-Agent-Run-Priority"),
                exchange.getRequestHeaders().getFirst("Last-Event-ID")));

        if ("/api/core/conversations".equals(path)) {
            json(exchange, 200, envelope("""
                    {"conversationId":31,"conversationCode":"THREAD_31","agentId":7,"title":"Java 契约测试","status":"active"}
                    """));
        } else if ("/api/core/conversations/31/messages/async".equals(path)) {
            json(exchange, 200, envelope("""
                    {"runCode":"RUN JAVA/1","conversationId":31,"agentId":7,"status":"queued"}
                    """));
        } else if ("/api/core/runtime/runs/RUN%20JAVA%2F1/events".equals(path)) {
            sse(exchange, streamCount.incrementAndGet());
        } else if ("/api/core/conversations/31/tasks/91/approvals/81/approve".equals(path)) {
            json(exchange, 200, envelope("""
                    {"taskId":91,"conversationId":31,"status":"running","approvals":[]}
                    """));
        } else {
            json(exchange, 404, "{\"code\":404,\"message\":\"not found\",\"data\":null}");
        }
    }

    private void sse(HttpExchange exchange, int connection) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        String content = connection == 1 ? """
                event: runtime.event
                data: {"eventId":"E1","sequenceNo":1,"runId":"RUN JAVA/1","taskId":91,"itemId":"81","eventType":"approval.requested","payload":{"approvalId":81},"metadata":{}}

                event: runtime.event
                data: {"eventId":"E2","sequenceNo":2,"runId":"RUN JAVA/1","taskId":91,"eventType":"run.waiting","payload":{"status":"waiting_approval"},"metadata":{}}

                """ : """
                event: runtime.event
                data: {"eventId":"E3","sequenceNo":3,"runId":"RUN JAVA/1","taskId":91,"eventType":"message.delta","payload":{"content":"审批后完成"},"metadata":{}}

                event: runtime.event
                data: {"eventId":"E4","sequenceNo":4,"runId":"RUN JAVA/1","taskId":91,"eventType":"run.completed","payload":{},"metadata":{}}

                """;
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private RequestSnapshot find(String method, String path) {
        RequestSnapshot snapshot = requests.stream()
                .filter(item -> method.equals(item.method()) && path.equals(item.path()))
                .findFirst()
                .orElse(null);
        assertNotNull(snapshot, method + " " + path + " 未被调用，实际请求=" + requests);
        return snapshot;
    }

    private static String envelope(String data) {
        return "{\"code\":200,\"message\":\"success\",\"data\":" + data.strip() + "}";
    }

    private static void json(HttpExchange exchange, int status, String content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RequestSnapshot(
            String method,
            String path,
            String query,
            String body,
            String authorization,
            String idempotencyKey,
            String priority,
            String lastEventId
    ) {
    }
}
