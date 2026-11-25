package com.zc.agent.sdk.runtime;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeClientTest {

    @Test
    void shouldUseStableThreadAndRunContract() {
        List<RuntimeHttpRequest> requests = new ArrayList<>();
        RuntimeClient client = RuntimeClient.builder("http://localhost:8081/api/core/")
                .accessTokenProvider(() -> "TOKEN_1")
                .requestTimeout(Duration.ofSeconds(5))
                .transport(request -> {
                    requests.add(request);
                    String data = request.uri().getPath().endsWith("/messages/async")
                            || request.uri().getPath().contains("/runs/")
                            ? runJson()
                            : threadJson();
                    return success(data);
                })
                .build();

        RuntimeThread created = client.createThread(new CreateRuntimeThreadRequest(7, "Java SDK"));
        client.getThread(9);
        RuntimeRun run = client.createRun(9, new CreateRuntimeRunRequest("hello"));
        client.getRun(9, "RUN/1");
        client.cancelRun(9, "RUN/1");

        assertEquals(9L, created.conversationId());
        assertEquals("RUN_1", run.runCode());
        assertEquals(List.of(
                "POST http://localhost:8081/api/core/conversations",
                "GET http://localhost:8081/api/core/conversations/9",
                "POST http://localhost:8081/api/core/conversations/9/messages/async",
                "GET http://localhost:8081/api/core/conversations/9/runs/RUN%2F1",
                "POST http://localhost:8081/api/core/conversations/9/runs/RUN%2F1/cancel"
        ), requests.stream().map(request -> request.method() + " " + request.uri()).toList());
        assertTrue(requests.stream().allMatch(request -> "Bearer TOKEN_1".equals(request.headers().get("Authorization"))));
        assertNotNull(requests.get(2).headers().get("Idempotency-Key"));
        assertEquals("{\"content\":\"hello\"}", requests.get(2).body());
        assertEquals(Duration.ofSeconds(5), requests.get(2).timeout());
    }

    @Test
    void shouldSupportExplicitRunOptionsAndDynamicToken() {
        List<RuntimeHttpRequest> requests = new ArrayList<>();
        AtomicInteger tokenVersion = new AtomicInteger();
        RuntimeClient client = RuntimeClient.builder("https://runtime.example/api/core")
                .accessTokenProvider(() -> "TOKEN_" + tokenVersion.incrementAndGet())
                .transport(request -> {
                    requests.add(request);
                    return success(runJson());
                })
                .build();

        client.createRun(3, new CreateRuntimeRunRequest("run"), new CreateRuntimeRunOptions("IDEMPOTENT_1", 80));
        client.getRun(3, "RUN_1");

        assertEquals("IDEMPOTENT_1", requests.get(0).headers().get("Idempotency-Key"));
        assertEquals("80", requests.get(0).headers().get("X-Agent-Run-Priority"));
        assertEquals("Bearer TOKEN_1", requests.get(0).headers().get("Authorization"));
        assertEquals("Bearer TOKEN_2", requests.get(1).headers().get("Authorization"));
    }

    @Test
    void shouldMapHttpApiInvalidTimeoutAndNetworkErrors() {
        AtomicInteger unauthorized = new AtomicInteger();
        RuntimeClient httpClient = RuntimeClient.builder("http://localhost/api/core")
                .onUnauthorized(unauthorized::incrementAndGet)
                .transport(request -> new RuntimeHttpResponse(401, Map.of(),
                        "{\"code\":401,\"message\":\"Token 已失效\",\"data\":null}"))
                .build();
        RuntimeSdkException httpError = assertThrows(RuntimeSdkException.class, () -> httpClient.getThread(1));
        assertEquals("HTTP_401", httpError.code());
        assertEquals("Token 已失效", httpError.getMessage());
        assertEquals(1, unauthorized.get());

        RuntimeClient apiClient = clientReturning(200, "{\"code\":409,\"message\":\"重复请求\",\"data\":null}");
        assertEquals("API_409", assertThrows(RuntimeSdkException.class, () -> apiClient.getThread(1)).code());

        RuntimeClient invalidClient = clientReturning(200, "not-json");
        assertEquals("INVALID_RESPONSE", assertThrows(RuntimeSdkException.class, () -> invalidClient.getThread(1)).code());

        RuntimeClient timeoutClient = RuntimeClient.builder("http://localhost/api/core")
                .transport(request -> { throw new HttpTimeoutException("timeout"); })
                .build();
        assertEquals("TIMEOUT", assertThrows(RuntimeSdkException.class, () -> timeoutClient.getThread(1)).code());

        RuntimeClient networkClient = RuntimeClient.builder("http://localhost/api/core")
                .transport(request -> { throw new IOException("down"); })
                .build();
        assertEquals("NETWORK_ERROR", assertThrows(RuntimeSdkException.class, () -> networkClient.getThread(1)).code());
    }

    @Test
    void shouldValidateConfigurationAndArguments() {
        assertEquals("INVALID_CONFIG", assertThrows(RuntimeSdkException.class,
                () -> RuntimeClient.builder("/api/core").build()).code());
        RuntimeClient client = clientReturning(200, threadJson());
        assertEquals("INVALID_ARGUMENT", assertThrows(RuntimeSdkException.class, () -> client.getThread(0)).code());
        assertEquals("INVALID_ARGUMENT", assertThrows(RuntimeSdkException.class,
                () -> client.createRun(1, new CreateRuntimeRunRequest(" "))).code());
        assertEquals("INVALID_ARGUMENT", assertThrows(RuntimeSdkException.class,
                () -> client.createRun(1, new CreateRuntimeRunRequest("run"),
                        new CreateRuntimeRunOptions(null, 101))).code());
    }

    @Test
    void shouldResumeRuntimeEventFromLastSequence() {
        List<RuntimeHttpRequest> streamRequests = new ArrayList<>();
        AtomicInteger connections = new AtomicInteger();
        RuntimeHttpTransport transport = new RuntimeHttpTransport() {
            @Override
            public RuntimeHttpResponse execute(RuntimeHttpRequest request) {
                return success(threadJson());
            }

            @Override
            public RuntimeHttpStreamResponse openStream(RuntimeHttpRequest request) {
                streamRequests.add(request);
                String body = connections.incrementAndGet() == 1
                        ? sse(event(1, "message.delta", "{\"content\":\"第一段\"}"),
                                event(2, "run.progress", "{\"message\":\"正在执行\"}"))
                        : sse(event(2, "message.delta", "{\"content\":\"重复\"}"),
                                event(3, "approval.requested", "{\"approvalId\":21}"),
                                event(4, "artifact.published", "{\"fileName\":\"计划.pdf\"}"),
                                event(5, "run.completed", "{\"status\":\"completed\"}"));
                return streamResponse(200, Map.of(), body);
            }
        };
        List<Long> sequences = new ArrayList<>();
        List<String> projections = new ArrayList<>();
        RuntimeClient client = RuntimeClient.builder("http://localhost/api/core")
                .accessTokenProvider(() -> "TOKEN_STREAM")
                .transport(transport)
                .build();

        RuntimeEventStreamResult result = client.streamEvents("RUN/1", new RuntimeEventListener() {
            @Override
            public void onEvent(RuntimeEventEnvelope event) {
                sequences.add(event.sequenceNo());
            }

            @Override
            public void onDelta(String content) {
                projections.add(content);
            }

            @Override
            public void onApprovalRequested(RuntimeEventEnvelope event) {
                projections.add("approval");
            }

            @Override
            public void onArtifactPublished(RuntimeEventEnvelope event) {
                projections.add("artifact");
            }
        }, new RuntimeEventStreamOptions(0, Duration.ofSeconds(5), Duration.ZERO, 3));

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), sequences);
        assertEquals(List.of("第一段", "approval", "artifact"), projections);
        assertEquals("completed", result.status());
        assertEquals(5, result.lastSequenceNo());
        assertEquals(List.of(
                "http://localhost/api/core/runtime/runs/RUN%2F1/events?afterSequence=0",
                "http://localhost/api/core/runtime/runs/RUN%2F1/events?afterSequence=2"
        ), streamRequests.stream().map(request -> request.uri().toString()).toList());
        assertEquals("2", streamRequests.get(1).headers().get("Last-Event-ID"));
        assertEquals("Bearer TOKEN_STREAM", streamRequests.get(1).headers().get("Authorization"));
    }

    @Test
    void shouldHandleApprovalRecoveryArtifactAndReplay() {
        List<RuntimeHttpRequest> requests = new ArrayList<>();
        RuntimeHttpTransport transport = new RuntimeHttpTransport() {
            @Override
            public RuntimeHttpResponse execute(RuntimeHttpRequest request) {
                requests.add(request);
                String path = request.uri().getPath();
                if (path.endsWith("/signal")) {
                    return new RuntimeHttpResponse(200, Map.of(), "{\"code\":200,\"message\":\"success\",\"data\":null}");
                }
                if (path.equals("/api/core/runtime/replays")) {
                    return success("{\"records\":[],\"total\":0,\"pageNum\":1,\"pageSize\":20}");
                }
                if (path.endsWith("/simulation")) {
                    return success("{\"replayCode\":\"REPLAY_1\",\"replayMode\":\"simulation_replay\","
                            + "\"sourceRunCode\":\"RUN/1\",\"sourceTaskId\":12,\"status\":\"succeeded\"}");
                }
                if (path.contains("/runtime/replays/")) {
                    return success("{\"replayCode\":\"REPLAY/1\",\"manifest\":{},\"inputSnapshot\":{},\"resultSnapshot\":{}}");
                }
                return success("{\"taskId\":12,\"conversationId\":9,\"status\":\"completed\",\"approvals\":[]}");
            }

            @Override
            public RuntimeHttpStreamResponse openStream(RuntimeHttpRequest request) {
                requests.add(request);
                return streamResponse(200, Map.of(
                        "Content-Type", List.of("application/pdf"),
                        "Content-Disposition", List.of("attachment; filename*=UTF-8''%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92.pdf")
                ), "pdf-content");
            }
        };
        RuntimeClient client = RuntimeClient.builder("http://localhost/api/core").transport(transport).build();

        client.resumeTask(9, 12);
        client.approveTask(9, 12, 21, new RuntimeApprovalActionRequest("确认", null));
        client.rejectTask(9, 12, 22, new RuntimeApprovalActionRequest("拒绝", true));
        client.revokeApproval(9, 12, 23, RuntimeApprovalActionRequest.empty());
        client.signalWait("order/paid", Map.of("orderId", 1001));
        RuntimeArtifactDownload artifact = client.downloadArtifact(9, 12, "reports/学习计划 (1).pdf");
        client.listReplays(new RuntimeReplayPageQuery(1, 20, "simulation_replay", "succeeded", "RUN/1"));
        client.getReplay("REPLAY/1");
        client.createSimulationReplay("RUN/1", new RuntimeSimulationReplayRequest(2L, 8L));

        assertEquals("学习计划.pdf", artifact.fileName());
        assertEquals("pdf-content", new String(artifact.data(), StandardCharsets.UTF_8));
        assertEquals("application/pdf", artifact.contentType());
        assertTrue(requests.stream().anyMatch(request -> request.uri().toString().contains(
                "/artifacts?path=reports%2F%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92+%281%29.pdf")));
        assertTrue(requests.stream().anyMatch(request -> request.uri().toString().contains(
                "/runtime/replays?pageNum=1&pageSize=20&replayMode=simulation_replay&status=succeeded&sourceRunCode=RUN%2F1")));
        assertEquals("INVALID_ARGUMENT", assertThrows(RuntimeSdkException.class,
                () -> client.createSimulationReplay("RUN_1", new RuntimeSimulationReplayRequest(9L, 8L))).code());
    }

    private static RuntimeClient clientReturning(int status, String body) {
        return RuntimeClient.builder("http://localhost/api/core")
                .transport(request -> new RuntimeHttpResponse(status, Map.of(), body))
                .build();
    }

    private static RuntimeHttpResponse success(String data) {
        return new RuntimeHttpResponse(200, Map.of(), "{\"code\":200,\"message\":\"success\",\"data\":" + data + "}");
    }

    private static RuntimeHttpStreamResponse streamResponse(int status,
                                                            Map<String, List<String>> headers,
                                                            String body) {
        return new RuntimeHttpStreamResponse(status, headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sse(String... events) {
        return String.join("", events);
    }

    private static String event(long sequenceNo, String eventType, String payload) {
        String envelope = "{\"eventId\":\"EVT_" + sequenceNo + "\",\"sequenceNo\":" + sequenceNo
                + ",\"threadId\":9,\"runId\":\"RUN/1\",\"taskId\":12,\"eventType\":\"" + eventType
                + "\",\"occurredAt\":\"2026-09-10T00:00:00Z\",\"payloadVersion\":1,\"payload\":"
                + payload + ",\"metadata\":{}}";
        return "id: " + sequenceNo + "\nevent: runtime.event\ndata: " + envelope + "\n\n";
    }

    private static String threadJson() {
        return "{\"conversationId\":9,\"conversationCode\":\"CONV_9\",\"agentId\":7,\"title\":\"SDK\",\"status\":\"active\"}";
    }

    private static String runJson() {
        return "{\"runCode\":\"RUN_1\",\"conversationId\":9,\"agentId\":7,\"status\":\"queued\","
                + "\"attemptCount\":0,\"maxAttempts\":2,\"priority\":50,\"taskId\":null,"
                + "\"errorMessage\":null,\"cancelRequested\":false}";
    }
}
