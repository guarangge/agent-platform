package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 按 Run 游标输出统一 Runtime Event SSE，事件内容完全来自持久化 Event Store。
 */
@Service
public class RuntimeEventStreamService {

    private static final Set<String> STREAM_CLOSED_STATUSES = Set.of(
            "completed", "waiting_approval", "waiting_event", "paused", "failed", "cancelled"
    );
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);
    private static final long POLL_INTERVAL_MS = 150L;
    private static final int BATCH_SIZE = 200;

    private final RuntimeEventQueryService queryService;
    private final ObjectMapper objectMapper;

    public RuntimeEventStreamService(RuntimeEventQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    /** 创建当前用户指定 Run 的统一事件流。 */
    public StreamingResponseBody stream(String runCode, long afterSequence, SecurityUser securityUser) {
        queryService.findAfter(runCode, afterSequence, 1, securityUser);
        return outputStream -> writeStream(outputStream, runCode, afterSequence, securityUser);
    }

    private void writeStream(OutputStream outputStream,
                             String runCode,
                             long initialSequence,
                             SecurityUser securityUser) throws IOException {
        long deadline = System.nanoTime() + STREAM_TIMEOUT.toNanos();
        long sequence = initialSequence;
        sendRaw(outputStream, "retry: 2000\n\n");
        while (System.nanoTime() < deadline) {
            RuntimeEventQueryService.RuntimeEventBatch batch = queryService.findAfter(
                    runCode, sequence, BATCH_SIZE, securityUser);
            List<RuntimeEventEnvelope> events = batch.events();
            for (RuntimeEventEnvelope event : events) {
                sequence = event.sequenceNo();
                sendEvent(outputStream, event);
            }
            if (events.size() >= BATCH_SIZE) {
                continue;
            }
            if (STREAM_CLOSED_STATUSES.contains(batch.run().status())) {
                return;
            }
            sleep(POLL_INTERVAL_MS);
        }
        sendRaw(outputStream, ": runtime event stream timeout\n\n");
    }

    private void sendEvent(OutputStream outputStream, RuntimeEventEnvelope event) throws IOException {
        sendRaw(outputStream, "id: " + event.sequenceNo()
                + "\nevent: runtime.event\ndata: " + objectMapper.writeValueAsString(event) + "\n\n");
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
            throw new IOException("Runtime Event SSE 线程被中断", exception);
        }
    }
}
