package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Java Runtime Event 阻塞消费与断线续传实现。 */
final class RuntimeEventStreamSupport {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "completed", "waiting_approval", "waiting_event", "paused", "failed", "cancelled");

    private final String baseUrl;
    private final Supplier<String> accessTokenProvider;
    private final RuntimeHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final Runnable onUnauthorized;

    RuntimeEventStreamSupport(String baseUrl,
                              Supplier<String> accessTokenProvider,
                              RuntimeHttpTransport transport,
                              ObjectMapper objectMapper,
                              Runnable onUnauthorized) {
        this.baseUrl = baseUrl;
        this.accessTokenProvider = accessTokenProvider;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.onUnauthorized = onUnauthorized;
    }

    RuntimeEventStreamResult stream(String runCode,
                                    RuntimeEventListener listener,
                                    RuntimeEventStreamOptions options) {
        RuntimeEventListener safeListener = listener == null ? new RuntimeEventListener() { } : listener;
        RuntimeEventStreamOptions safeOptions = validate(options);
        Instant deadline = Instant.now().plus(safeOptions.timeout());
        long cursor = safeOptions.afterSequence();
        int emptyConnections = 0;
        while (Instant.now().isBefore(deadline)) {
            throwIfInterrupted();
            ConnectionResult connection = consumeConnection(runCode, cursor, deadline, safeListener);
            cursor = Math.max(cursor, connection.lastSequenceNo());
            if (connection.terminal() != null) {
                return connection.terminal();
            }
            emptyConnections = connection.eventCount() == 0 ? emptyConnections + 1 : 0;
            if (emptyConnections >= safeOptions.maxEmptyConnections()) {
                throw new RuntimeSdkException("Runtime Event 连接已结束但未收到 Run 终态", "STREAM_CLOSED");
            }
            sleep(safeOptions.reconnectDelay());
        }
        throw new RuntimeSdkException("Runtime Event 等待超时，请稍后查询 Run 状态", "STREAM_TIMEOUT");
    }

    private ConnectionResult consumeConnection(String runCode,
                                                long cursor,
                                                Instant deadline,
                                                RuntimeEventListener listener) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new RuntimeSdkException("Runtime Event 等待超时，请稍后查询 Run 状态", "STREAM_TIMEOUT");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/event-stream");
        String token = accessTokenProvider == null ? null : accessTokenProvider.get();
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
        if (cursor > 0) {
            headers.put("Last-Event-ID", String.valueOf(cursor));
        }
        String path = "/runtime/runs/" + encodePathSegment(runCode) + "/events?afterSequence=" + cursor;
        RuntimeHttpRequest request = new RuntimeHttpRequest(
                URI.create(baseUrl + path), "GET", Map.copyOf(headers), null, remaining);
        try (RuntimeHttpStreamResponse response = transport.openStream(request)) {
            if (response.statusCode() == 401 && onUnauthorized != null) {
                onUnauthorized.run();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeSdkException("Runtime Event 连接失败，HTTP " + response.statusCode(),
                        "HTTP_" + response.statusCode(), response.statusCode());
            }
            return readFrames(response, cursor, listener, deadline);
        } catch (RuntimeSdkException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeSdkException("Runtime Event 消费已中断", "INTERRUPTED", null, exception);
        } catch (IOException exception) {
            throw new RuntimeSdkException("Runtime Event 网络请求失败: " + exception.getMessage(),
                    "STREAM_NETWORK_ERROR", null, exception);
        }
    }

    private ConnectionResult readFrames(RuntimeHttpStreamResponse response,
                                        long initialCursor,
                                        RuntimeEventListener listener,
                                        Instant deadline) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        long cursor = initialCursor;
        int eventCount = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            throwIfInterrupted();
            if (Instant.now().isAfter(deadline)) {
                throw new RuntimeSdkException("Runtime Event 等待超时，请稍后查询 Run 状态", "STREAM_TIMEOUT");
            }
            if (line.isEmpty()) {
                RuntimeEventEnvelope event = parseEnvelope(eventName, data.toString());
                if (event != null && event.sequenceNo() > cursor) {
                    cursor = event.sequenceNo();
                    eventCount++;
                    RuntimeEventStreamResult terminal = dispatch(event, listener);
                    if (terminal != null) {
                        return new ConnectionResult(eventCount, cursor, terminal);
                    }
                }
                eventName = "message";
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).stripLeading());
            }
        }
        return new ConnectionResult(eventCount, cursor, null);
    }

    private RuntimeEventEnvelope parseEnvelope(String eventName, String data) {
        if (!"runtime.event".equals(eventName) || data.isBlank()) {
            return null;
        }
        try {
            RuntimeEventEnvelope event = objectMapper.readValue(data, RuntimeEventEnvelope.class);
            return event.sequenceNo() != null && event.sequenceNo() > 0
                    && event.eventType() != null && !event.eventType().isBlank() ? event : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private RuntimeEventStreamResult dispatch(RuntimeEventEnvelope event, RuntimeEventListener listener) {
        listener.onEvent(event);
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        Map<String, Object> metadata = event.metadata() == null ? Map.of() : event.metadata();
        if ("message.reset".equals(event.eventType())) {
            listener.onReset();
        } else if ("message.delta".equals(event.eventType())) {
            listener.onDelta(text(payload.get("content")));
        } else if ("run.progress".equals(event.eventType())) {
            listener.onProgress(new RuntimeEventProgress(
                    fallback(text(metadata.get("stage")), "execution"),
                    fallback(text(payload.get("message")), "正在执行任务"),
                    event.sequenceNo(), integer(metadata.get("stepNo")), integer(metadata.get("stepCount")),
                    nullableText(metadata.get("stepType"))));
        } else if ("approval.requested".equals(event.eventType())) {
            listener.onApprovalRequested(event);
        } else if ("artifact.published".equals(event.eventType())) {
            listener.onArtifactPublished(event);
        }
        String status = resolveStatus(event.eventType(), payload);
        if (status == null) {
            return null;
        }
        Long taskId = event.taskId() == null ? longValue(payload.get("taskId")) : event.taskId();
        listener.onStatus(status, taskId);
        return TERMINAL_STATUSES.contains(status)
                ? new RuntimeEventStreamResult(status, taskId, nullableText(payload.get("errorMessage")), event.sequenceNo())
                : null;
    }

    private static String resolveStatus(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "run.queued" -> "queued";
            case "run.started" -> "running";
            case "run.completed" -> "completed";
            case "run.failed" -> "failed";
            case "run.cancelled" -> "cancelled";
            case "run.waiting" -> fallback(text(payload.get("status")), "waiting_event");
            default -> null;
        };
    }

    private static RuntimeEventStreamOptions validate(RuntimeEventStreamOptions options) {
        RuntimeEventStreamOptions value = options == null ? RuntimeEventStreamOptions.defaults() : options;
        if (value.afterSequence() < 0 || value.timeout() == null || value.timeout().isNegative() || value.timeout().isZero()
                || value.reconnectDelay() == null || value.reconnectDelay().isNegative()
                || value.maxEmptyConnections() <= 0) {
            throw new RuntimeSdkException("Runtime Event 消费参数无效", "INVALID_ARGUMENT");
        }
        return value;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeSdkException("Runtime Event 消费已中断", "INTERRUPTED", null, exception);
        }
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeSdkException("Runtime Event 消费已中断", "INTERRUPTED");
        }
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableText(Object value) {
        String text = text(value);
        return text.isBlank() ? null : text;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record ConnectionResult(int eventCount, long lastSequenceNo, RuntimeEventStreamResult terminal) {
    }
}
