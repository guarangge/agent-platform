package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 使用 PostgreSQL Run Event Store 持久化 Runtime Event。
 */
@Service
public class PersistentRuntimeEventPublisher implements RuntimeEventPublisher {

    private static final int PAYLOAD_VERSION = 1;
    private final AgentRunStreamEventRepository repository;
    private final ObjectMapper objectMapper;

    public PersistentRuntimeEventPublisher(AgentRunStreamEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public long publish(String runCode,
                        RuntimeEventType eventType,
                        String content,
                        Map<String, Object> metadata) {
        return append(runCode, eventType, content == null ? "" : content, metadata);
    }

    @Override
    public long publishPayload(String runCode,
                               RuntimeEventType eventType,
                               Map<String, Object> payload,
                               Map<String, Object> metadata) {
        try {
            return append(runCode, eventType,
                    objectMapper.writeValueAsString(payload == null ? Map.of() : payload), metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Runtime Event Payload 序列化失败", exception);
        }
    }

    private long append(String runCode,
                        RuntimeEventType eventType,
                        String content,
                        Map<String, Object> metadata) {
        try {
            return repository.append(runCode, eventType.code(), content,
                    objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata), PAYLOAD_VERSION);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Runtime Event 元数据序列化失败", exception);
        }
    }
}
