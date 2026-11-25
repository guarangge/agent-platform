package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 任务追踪服务。
 * 负责将运行时关键事件标准化写入 task_traces。
 */
@Service
public class TaskTraceService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final TraceDataSanitizer traceDataSanitizer;

    public TaskTraceService(ConversationRepository conversationRepository,
                            ObjectMapper objectMapper,
                            TraceDataSanitizer traceDataSanitizer) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.traceDataSanitizer = traceDataSanitizer;
    }

    public void recordSpan(Long taskId,
                           String traceId,
                           Long taskStepId,
                           String eventType,
                           String spanName,
                           String traceStatus,
                           Object inputSnapshot,
                           Object outputSnapshot,
                           String errorMessage,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           Object metadata,
                           Long operatorId) {
        LocalDateTime safeStartedAt = startedAt == null ? LocalDateTime.now() : startedAt;
        LocalDateTime safeFinishedAt = finishedAt == null ? safeStartedAt : finishedAt;
        long durationMs = Math.max(0L, Duration.between(safeStartedAt, safeFinishedAt).toMillis());
        conversationRepository.insertTaskTrace(
                taskId,
                traceId,
                taskStepId,
                eventType,
                spanName,
                traceStatus,
                toJsonText(traceDataSanitizer.sanitize(inputSnapshot)),
                toJsonText(traceDataSanitizer.sanitize(outputSnapshot)),
                traceDataSanitizer.sanitizeError(errorMessage),
                safeStartedAt,
                safeFinishedAt,
                durationMs,
                toJsonText(traceDataSanitizer.sanitize(metadata)),
                operatorId
        );
    }

    private String toJsonText(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("追踪事件序列化失败");
        }
    }
}
