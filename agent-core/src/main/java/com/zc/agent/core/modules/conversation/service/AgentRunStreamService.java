package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将持久化 Agent Run 转换为可断线重建的 SSE 事件流。
 * 状态和最终消息均读取共享数据库，因此不依赖单机内存事件总线。
 */
@Service
public class AgentRunStreamService {

    private static final Set<String> CONTENT_STATUSES = Set.of("completed", "waiting_approval", "waiting_event", "paused");
    private static final Set<String> ERROR_STATUSES = Set.of("failed", "cancelled");
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);
    private static final long POLL_INTERVAL_MS = 150L;
    private static final int DELTA_CODE_POINTS = 24;

    private final AgentRunService agentRunService;
    private final ConversationRepository conversationRepository;
    private final AgentRunStreamEventService streamEventService;
    private final ObjectMapper objectMapper;

    public AgentRunStreamService(AgentRunService agentRunService,
                                 ConversationRepository conversationRepository,
                                 AgentRunStreamEventService streamEventService,
                                 ObjectMapper objectMapper) {
        this.agentRunService = agentRunService;
        this.conversationRepository = conversationRepository;
        this.streamEventService = streamEventService;
        this.objectMapper = objectMapper;
    }

    /** 创建当前用户指定 Run 的 SSE 输出体。 */
    public StreamingResponseBody stream(Long conversationId, String runCode, SecurityUser securityUser) {
        AgentRunResponse initial = requireConversationRun(conversationId, runCode, securityUser);
        return outputStream -> writeStream(outputStream, conversationId, initial, securityUser);
    }

    private void writeStream(OutputStream outputStream,
                             Long conversationId,
                             AgentRunResponse initial,
                             SecurityUser securityUser) throws IOException {
        long deadline = System.nanoTime() + STREAM_TIMEOUT.toNanos();
        String previousStatus = null;
        long eventSequence = 0L;
        boolean nativeContentObserved = false;
        AgentRunResponse run = initial;
        sendRaw(outputStream, "retry: 2000\n\n");
        while (System.nanoTime() < deadline) {
            StreamCursor cursor = forwardNativeEvents(outputStream, run.runCode(), eventSequence, nativeContentObserved);
            eventSequence = cursor.sequenceNo();
            nativeContentObserved = cursor.nativeContentObserved();
            if (!run.status().equals(previousStatus)) {
                sendEvent(outputStream, "status", statusPayload(run));
                previousStatus = run.status();
            }
            if (CONTENT_STATUSES.contains(run.status())) {
                String finalContent = resolveAssistantContent(run.taskId());
                if (nativeContentObserved) {
                    sendEvent(outputStream, "replace", Map.of("content", finalContent));
                } else {
                    streamFinalContent(outputStream, finalContent);
                }
                sendEvent(outputStream, "completed", statusPayload(run));
                return;
            }
            if (ERROR_STATUSES.contains(run.status())) {
                Map<String, Object> payload = statusPayload(run);
                payload.put("message", run.errorMessage() == null ? "Agent 执行失败" : run.errorMessage());
                sendEvent(outputStream, "failed", payload);
                return;
            }
            sleep(POLL_INTERVAL_MS);
            run = requireConversationRun(conversationId, run.runCode(), securityUser);
        }
        sendEvent(outputStream, "failed", Map.of(
                "runCode", initial.runCode(),
                "status", "timeout",
                "message", "流式连接等待超时，Run 仍可能在后台执行"
        ));
    }

    private StreamCursor forwardNativeEvents(OutputStream outputStream,
                                             String runCode,
                                             long initialSequence,
                                             boolean initialObserved) throws IOException {
        long sequence = initialSequence;
        boolean observed = initialObserved;
        while (true) {
            List<AgentRunStreamEventRecord> events = streamEventService.findAfter(runCode, sequence);
            if (events.isEmpty()) return new StreamCursor(sequence, observed);
            for (AgentRunStreamEventRecord event : events) {
                sequence = event.sequenceNo();
                RuntimeEventType eventType;
                try {
                    eventType = RuntimeEventType.fromStoredCode(event.eventType());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (eventType == RuntimeEventType.MESSAGE_RESET) {
                    observed = true;
                    sendEvent(outputStream, "reset", Map.of("sequenceNo", sequence));
                } else if (eventType == RuntimeEventType.MESSAGE_DELTA) {
                    observed = true;
                    sendEvent(outputStream, "delta", Map.of(
                            "content", event.content(),
                            "sequenceNo", sequence
                    ));
                } else if (eventType == RuntimeEventType.RUN_PROGRESS) {
                    Map<String, Object> payload = parseMetadata(event.metadata());
                    payload.put("message", event.content());
                    payload.put("sequenceNo", sequence);
                    sendEvent(outputStream, "progress", payload);
                }
            }
            if (events.size() < 200) return new StreamCursor(sequence, observed);
        }
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() { }));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void streamFinalContent(OutputStream outputStream, String content) throws IOException {
        if (content.isBlank()) return;
        int[] codePoints = content.codePoints().toArray();
        for (int start = 0; start < codePoints.length; start += DELTA_CODE_POINTS) {
            int length = Math.min(DELTA_CODE_POINTS, codePoints.length - start);
            String chunk = new String(codePoints, start, length);
            sendEvent(outputStream, "delta", Map.of("content", chunk));
            sleep(20L);
        }
    }

    private String resolveAssistantContent(Long taskId) {
        if (taskId == null) return "";
        return conversationRepository.findAssistantMessageByTaskId(taskId)
                .map(MessageRecord::content)
                .orElse("");
    }

    private AgentRunResponse requireConversationRun(Long conversationId,
                                                    String runCode,
                                                    SecurityUser securityUser) {
        AgentRunResponse run = agentRunService.get(runCode, securityUser);
        if (!conversationId.equals(run.conversationId())) {
            throw new BusinessException("异步运行不属于当前会话");
        }
        return run;
    }

    private Map<String, Object> statusPayload(AgentRunResponse run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runCode", run.runCode());
        payload.put("status", run.status());
        payload.put("taskId", run.taskId());
        if (run.errorMessage() != null) payload.put("errorMessage", run.errorMessage());
        return payload;
    }

    private void sendEvent(OutputStream outputStream, String event, Object payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        sendRaw(outputStream, "event: " + event + "\ndata: " + json + "\n\n");
    }

    private void sendRaw(OutputStream outputStream, String value) throws IOException {
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void sleep(long milliseconds) throws IOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("流式响应线程被中断", exception);
        }
    }

    private record StreamCursor(long sequenceNo, boolean nativeContentObserved) {
    }
}
