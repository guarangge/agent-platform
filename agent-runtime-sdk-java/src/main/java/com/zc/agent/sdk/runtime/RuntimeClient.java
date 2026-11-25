package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Agent Runtime Java Client。
 *
 * <p>对业务系统隐藏当前 Conversation Controller 路径和统一响应包。</p>
 */
public final class RuntimeClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration LONG_RUNNING_TIMEOUT = Duration.ofMinutes(10);

    private final String baseUrl;
    private final Supplier<String> accessTokenProvider;
    private final RuntimeHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Runnable onUnauthorized;

    private RuntimeClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.accessTokenProvider = builder.accessTokenProvider;
        this.transport = builder.transport == null ? new JdkRuntimeHttpTransport() : builder.transport;
        this.objectMapper = builder.objectMapper == null ? defaultObjectMapper() : builder.objectMapper;
        this.requestTimeout = positiveTimeout(builder.requestTimeout);
        this.onUnauthorized = builder.onUnauthorized;
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /** 创建 Thread。 */
    public RuntimeThread createThread(CreateRuntimeThreadRequest request) {
        if (request == null || request.agentId() <= 0) {
            throw new RuntimeSdkException("agentId 必须为正整数", "INVALID_ARGUMENT");
        }
        return request("POST", "/conversations", request, Map.of(), RuntimeThread.class);
    }

    /** 查询 Thread。 */
    public RuntimeThread getThread(long threadId) {
        return request("GET", "/conversations/" + positiveId(threadId, "threadId"), null, Map.of(), RuntimeThread.class);
    }

    /** 创建异步 Run；未指定时自动生成 Idempotency-Key。 */
    public RuntimeRun createRun(long threadId, CreateRuntimeRunRequest request) {
        return createRun(threadId, request, CreateRuntimeRunOptions.defaults());
    }

    /** 创建带幂等键或优先级的异步 Run。 */
    public RuntimeRun createRun(long threadId,
                                CreateRuntimeRunRequest request,
                                CreateRuntimeRunOptions options) {
        if (request == null || isBlank(request.content())) {
            throw new RuntimeSdkException("content 不能为空", "INVALID_ARGUMENT");
        }
        CreateRuntimeRunOptions safeOptions = options == null ? CreateRuntimeRunOptions.defaults() : options;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Idempotency-Key", isBlank(safeOptions.idempotencyKey())
                ? UUID.randomUUID().toString()
                : safeOptions.idempotencyKey().trim());
        if (safeOptions.priority() != null) {
            if (safeOptions.priority() < 0 || safeOptions.priority() > 100) {
                throw new RuntimeSdkException("priority 必须在 0 到 100 之间", "INVALID_ARGUMENT");
            }
            headers.put("X-Agent-Run-Priority", String.valueOf(safeOptions.priority()));
        }
        return request("POST",
                "/conversations/" + positiveId(threadId, "threadId") + "/messages/async",
                request,
                headers,
                RuntimeRun.class);
    }

    /** 查询 Run。 */
    public RuntimeRun getRun(long threadId, String runCode) {
        return request("GET", runPath(threadId, runCode), null, Map.of(), RuntimeRun.class);
    }

    /** 请求取消 Run。 */
    public RuntimeRun cancelRun(long threadId, String runCode) {
        return request("POST", runPath(threadId, runCode) + "/cancel", null, Map.of(), RuntimeRun.class);
    }

    /** 阻塞消费 Runtime Event，并在非终态断连后从最后序号续传。 */
    public RuntimeEventStreamResult streamEvents(String runCode, RuntimeEventListener listener) {
        return streamEvents(runCode, listener, RuntimeEventStreamOptions.defaults());
    }

    /** 使用指定游标和重连策略阻塞消费 Runtime Event。 */
    public RuntimeEventStreamResult streamEvents(String runCode,
                                                 RuntimeEventListener listener,
                                                 RuntimeEventStreamOptions options) {
        return new RuntimeEventStreamSupport(baseUrl, accessTokenProvider, transport, objectMapper, onUnauthorized)
                .stream(required(runCode, "runCode"), listener, options);
    }

    /** 从失败步骤恢复 Task。 */
    public RuntimeTask resumeTask(long threadId, long taskId) {
        return request("POST", taskPath(threadId, taskId) + "/resume", null, Map.of(),
                RuntimeTask.class, LONG_RUNNING_TIMEOUT);
    }

    /** 审批通过并从原 Checkpoint 继续 Task。 */
    public RuntimeTask approveTask(long threadId, long taskId, long approvalId) {
        return approveTask(threadId, taskId, approvalId, RuntimeApprovalActionRequest.empty());
    }

    /** 审批通过并从原 Checkpoint 继续 Task。 */
    public RuntimeTask approveTask(long threadId,
                                   long taskId,
                                   long approvalId,
                                   RuntimeApprovalActionRequest request) {
        return submitApproval(threadId, taskId, approvalId, "approve", request);
    }

    /** 审批拒绝并按请求决定是否重新规划。 */
    public RuntimeTask rejectTask(long threadId, long taskId, long approvalId) {
        return rejectTask(threadId, taskId, approvalId, RuntimeApprovalActionRequest.empty());
    }

    /** 审批拒绝并按请求决定是否重新规划。 */
    public RuntimeTask rejectTask(long threadId,
                                  long taskId,
                                  long approvalId,
                                  RuntimeApprovalActionRequest request) {
        return submitApproval(threadId, taskId, approvalId, "reject", request);
    }

    /** 撤回尚未处理的审批。 */
    public RuntimeTask revokeApproval(long threadId, long taskId, long approvalId) {
        return revokeApproval(threadId, taskId, approvalId, RuntimeApprovalActionRequest.empty());
    }

    /** 撤回尚未处理的审批。 */
    public RuntimeTask revokeApproval(long threadId,
                                      long taskId,
                                      long approvalId,
                                      RuntimeApprovalActionRequest request) {
        return submitApproval(threadId, taskId, approvalId, "revoke", request);
    }

    /** 通过稳定事件键唤醒 waiting_event Task。 */
    public void signalWait(String eventKey, Map<String, Object> payload) {
        request("POST", "/conversations/waits/" + encodePathSegment(required(eventKey, "eventKey")) + "/signal",
                payload == null ? Map.of() : payload, Map.of(), Void.class);
    }

    /** 下载受保护的 Task Artifact。 */
    public RuntimeArtifactDownload downloadArtifact(long threadId, long taskId, String path) {
        String safePath = required(path, "path");
        String requestPath = taskPath(threadId, taskId) + "/artifacts?path=" + encodeQueryValue(safePath);
        Map<String, String> headers = authenticatedHeaders("application/octet-stream", false);
        try (RuntimeHttpStreamResponse response = openStream(requestPath, headers, requestTimeout)) {
            byte[] data = response.body().readAllBytes();
            String fileName = parseContentDispositionFileName(header(response.headers(), "content-disposition"));
            if (isBlank(fileName)) {
                int separator = safePath.lastIndexOf('/');
                fileName = separator >= 0 ? safePath.substring(separator + 1) : safePath;
            }
            String contentType = header(response.headers(), "content-type");
            return new RuntimeArtifactDownload(data, fileName,
                    isBlank(contentType) ? "application/octet-stream" : contentType, data.length);
        } catch (RuntimeSdkException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RuntimeSdkException("Artifact 读取失败: " + exception.getMessage(),
                    "NETWORK_ERROR", null, exception);
        }
    }

    /** 分页查询 Replay 独立审计实例。 */
    public RuntimeReplayPage listReplays(RuntimeReplayPageQuery query) {
        if (query == null || query.pageNum() <= 0 || query.pageSize() <= 0 || query.pageSize() > 100) {
            throw new RuntimeSdkException("Replay 分页参数无效", "INVALID_ARGUMENT");
        }
        StringBuilder path = new StringBuilder("/runtime/replays?pageNum=").append(query.pageNum())
                .append("&pageSize=").append(query.pageSize());
        appendQuery(path, "replayMode", query.replayMode());
        appendQuery(path, "status", query.status());
        appendQuery(path, "sourceRunCode", query.sourceRunCode());
        return request("GET", path.toString(), null, Map.of(), RuntimeReplayPage.class);
    }

    /** 查询 Replay 的 Manifest、Input 和独立结果。 */
    public RuntimeReplayDetail getReplay(String replayCode) {
        return request("GET", "/runtime/replays/" + encodePathSegment(required(replayCode, "replayCode")),
                null, Map.of(), RuntimeReplayDetail.class);
    }

    /** 使用冻结 Runtime Event 发起无副作用 Simulation Replay。 */
    public RuntimeSimulationReplayResult createSimulationReplay(String runCode) {
        return createSimulationReplay(runCode, RuntimeSimulationReplayRequest.fullRange());
    }

    /** 使用指定事件范围发起无副作用 Simulation Replay。 */
    public RuntimeSimulationReplayResult createSimulationReplay(String runCode,
                                                                 RuntimeSimulationReplayRequest request) {
        RuntimeSimulationReplayRequest safeRequest = request == null
                ? RuntimeSimulationReplayRequest.fullRange() : request;
        validateReplayRange(safeRequest);
        return request("POST", "/runtime/replays/runs/" + encodePathSegment(required(runCode, "runCode"))
                        + "/simulation", safeRequest, Map.of(), RuntimeSimulationReplayResult.class,
                LONG_RUNNING_TIMEOUT);
    }

    private RuntimeTask submitApproval(long threadId,
                                       long taskId,
                                       long approvalId,
                                       String action,
                                       RuntimeApprovalActionRequest request) {
        String path = taskPath(threadId, taskId) + "/approvals/" + positiveId(approvalId, "approvalId")
                + "/" + action;
        return request("POST", path, request == null ? RuntimeApprovalActionRequest.empty() : request,
                Map.of(), RuntimeTask.class, LONG_RUNNING_TIMEOUT);
    }

    private <T> T request(String method,
                          String path,
                          Object body,
                          Map<String, String> extraHeaders,
                          Class<T> responseType) {
        return request(method, path, body, extraHeaders, responseType, requestTimeout);
    }

    private <T> T request(String method,
                          String path,
                          Object body,
                          Map<String, String> extraHeaders,
                          Class<T> responseType,
                          Duration timeout) {
        Map<String, String> headers = authenticatedHeaders("application/json", body != null);
        headers.putAll(extraHeaders);
        RuntimeHttpRequest httpRequest = new RuntimeHttpRequest(
                URI.create(baseUrl + path),
                method,
                Map.copyOf(headers),
                serialize(body),
                timeout
        );
        try {
            RuntimeHttpResponse response = transport.execute(httpRequest);
            if (response.statusCode() == 401 && onUnauthorized != null) {
                onUnauthorized.run();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeSdkException(
                        responseMessage(response.body(), "Runtime 请求失败，HTTP " + response.statusCode()),
                        "HTTP_" + response.statusCode(),
                        response.statusCode());
            }
            return parseEnvelope(response, responseType);
        } catch (RuntimeSdkException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new RuntimeSdkException("Runtime 请求超时", "TIMEOUT", null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeSdkException("Runtime 请求已中断", "INTERRUPTED", null, exception);
        } catch (IOException exception) {
            throw new RuntimeSdkException("Runtime 网络请求失败: " + exception.getMessage(),
                    "NETWORK_ERROR", null, exception);
        }
    }

    private <T> T parseEnvelope(RuntimeHttpResponse response, Class<T> responseType) {
        try {
            JavaType envelopeType = objectMapper.getTypeFactory().constructParametricType(ApiEnvelope.class, responseType);
            ApiEnvelope<T> envelope = objectMapper.readValue(response.body(), envelopeType);
            if (envelope.code() != 200) {
                throw new RuntimeSdkException(
                        isBlank(envelope.message()) ? "Runtime 请求失败" : envelope.message(),
                        "API_" + envelope.code(),
                        response.statusCode());
            }
            if (envelope.data() == null && responseType != Void.class) {
                throw new RuntimeSdkException("Runtime 响应缺少 data", "INVALID_RESPONSE", response.statusCode());
            }
            return envelope.data();
        } catch (RuntimeSdkException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new RuntimeSdkException("Runtime 响应格式无效", "INVALID_RESPONSE",
                    response.statusCode(), exception);
        }
    }

    private String serialize(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new RuntimeSdkException("Runtime 请求序列化失败", "INVALID_ARGUMENT", null, exception);
        }
    }

    private RuntimeHttpStreamResponse openStream(String path,
                                                 Map<String, String> headers,
                                                 Duration timeout) {
        RuntimeHttpRequest request = new RuntimeHttpRequest(
                URI.create(baseUrl + path), "GET", Map.copyOf(headers), null, timeout);
        try {
            RuntimeHttpStreamResponse response = transport.openStream(request);
            if (response.statusCode() == 401 && onUnauthorized != null) {
                onUnauthorized.run();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (response) {
                    String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeSdkException(
                            responseMessage(body, "Runtime 请求失败，HTTP " + response.statusCode()),
                            "HTTP_" + response.statusCode(), response.statusCode());
                }
            }
            return response;
        } catch (RuntimeSdkException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new RuntimeSdkException("Runtime 请求超时", "TIMEOUT", null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeSdkException("Runtime 请求已中断", "INTERRUPTED", null, exception);
        } catch (IOException exception) {
            throw new RuntimeSdkException("Runtime 网络请求失败: " + exception.getMessage(),
                    "NETWORK_ERROR", null, exception);
        }
    }

    private Map<String, String> authenticatedHeaders(String accept, boolean jsonBody) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        if (jsonBody) {
            headers.put("Content-Type", "application/json");
        }
        String token = accessTokenProvider == null ? null : accessTokenProvider.get();
        if (!isBlank(token)) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
        return headers;
    }

    private String responseMessage(String body, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("message").asText(null);
            return isBlank(message) ? fallback : message;
        } catch (JsonProcessingException ignored) {
            return fallback;
        }
    }

    private static String runPath(long threadId, String runCode) {
        return "/conversations/" + positiveId(threadId, "threadId")
                + "/runs/" + encodePathSegment(required(runCode, "runCode"));
    }

    private static String taskPath(long threadId, long taskId) {
        return "/conversations/" + positiveId(threadId, "threadId")
                + "/tasks/" + positiveId(taskId, "taskId");
    }

    private static void appendQuery(StringBuilder path, String key, String value) {
        if (!isBlank(value)) {
            path.append('&').append(key).append('=').append(encodeQueryValue(value.trim()));
        }
    }

    private static void validateReplayRange(RuntimeSimulationReplayRequest request) {
        Long from = request.fromSequence();
        Long to = request.toSequence();
        if ((from != null && from <= 0) || (to != null && to <= 0) || (from != null && to != null && from > to)) {
            throw new RuntimeSdkException("Replay 事件范围无效", "INVALID_ARGUMENT");
        }
    }

    private static String header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    private static String parseContentDispositionFileName(String contentDisposition) {
        if (isBlank(contentDisposition)) {
            return null;
        }
        java.util.regex.Matcher utf8 = java.util.regex.Pattern.compile("filename\\*=UTF-8''([^;]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        if (utf8.find()) {
            return java.net.URLDecoder.decode(utf8.group(1).replace("+", "%2B"), StandardCharsets.UTF_8);
        }
        java.util.regex.Matcher plain = java.util.regex.Pattern.compile("filename=\\\"?([^\\\";]+)\\\"?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        return plain.find() ? plain.group(1) : null;
    }

    private static long positiveId(long value, String field) {
        if (value <= 0) {
            throw new RuntimeSdkException(field + " 必须为正整数", "INVALID_ARGUMENT");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new RuntimeSdkException(field + " 不能为空", "INVALID_ARGUMENT");
        }
        return value.trim();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String value) {
        if (isBlank(value)) {
            throw new RuntimeSdkException("Runtime SDK baseUrl 不能为空", "INVALID_CONFIG");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new RuntimeSdkException("Runtime SDK baseUrl 无效", "INVALID_CONFIG", null, exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || isBlank(uri.getHost())) {
            throw new RuntimeSdkException("Runtime SDK baseUrl 必须使用 http 或 https", "INVALID_CONFIG");
        }
        return normalized;
    }

    private static Duration positiveTimeout(Duration value) {
        Duration timeout = value == null ? DEFAULT_TIMEOUT : value;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new RuntimeSdkException("requestTimeout 必须大于 0", "INVALID_CONFIG");
        }
        return timeout;
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiEnvelope<T>(int code, String message, T data) {
    }

    /** RuntimeClient 构建器。 */
    public static final class Builder {

        private final String baseUrl;
        private Supplier<String> accessTokenProvider;
        private RuntimeHttpTransport transport;
        private ObjectMapper objectMapper;
        private Duration requestTimeout = DEFAULT_TIMEOUT;
        private Runnable onUnauthorized;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Builder accessTokenProvider(Supplier<String> accessTokenProvider) {
            this.accessTokenProvider = accessTokenProvider;
            return this;
        }

        public Builder transport(RuntimeHttpTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder onUnauthorized(Runnable onUnauthorized) {
            this.onUnauthorized = onUnauthorized;
            return this;
        }

        public RuntimeClient build() {
            return new RuntimeClient(this);
        }
    }
}
