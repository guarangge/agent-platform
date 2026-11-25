package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验 Run 访问权限，并将持久化事件投影为统一 Runtime Event Envelope。
 */
@Service
public class RuntimeEventQueryService {

    private static final int MAX_BATCH_SIZE = 500;
    private final AgentRunService agentRunService;
    private final AgentRunStreamEventRepository repository;
    private final ObjectMapper objectMapper;

    public RuntimeEventQueryService(AgentRunService agentRunService,
                                    AgentRunStreamEventRepository repository,
                                    ObjectMapper objectMapper) {
        this.agentRunService = agentRunService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 查询游标后的 Runtime Event，并确保 Run 属于当前用户。 */
    public RuntimeEventBatch findAfter(String runCode,
                                       long afterSequence,
                                       int limit,
                                       SecurityUser securityUser) {
        if (afterSequence < 0) {
            throw new BusinessException("Runtime Event 游标不能小于0");
        }
        AgentRunResponse run = agentRunService.get(runCode, securityUser);
        List<RuntimeEventEnvelope> events = repository.findAfter(
                        runCode,
                        afterSequence,
                        Math.max(1, Math.min(limit, MAX_BATCH_SIZE))
                ).stream()
                .map(event -> toEnvelope(run, event))
                .toList();
        return new RuntimeEventBatch(run, events);
    }

    private RuntimeEventEnvelope toEnvelope(AgentRunResponse run, AgentRunStreamEventRecord event) {
        RuntimeEventType eventType = resolveKnownType(event.eventType());
        Map<String, Object> metadata = parseMetadata(event.metadata());
        String protocolEventType = eventType == null ? event.eventType() : eventType.code();
        Map<String, Object> payload = buildPayload(eventType, event.content());
        if (eventType != null && !protocolEventType.equals(event.eventType())) {
            metadata.put("legacyEventType", event.eventType());
        }
        return new RuntimeEventEnvelope(
                "EVT_" + event.eventId(),
                event.sequenceNo(),
                run.conversationId(),
                null,
                run.runCode(),
                run.taskId(),
                resolveItemId(eventType, payload),
                protocolEventType,
                event.createdAt(),
                event.payloadVersion(),
                payload,
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
        );
    }

    private RuntimeEventType resolveKnownType(String storedCode) {
        try {
            return RuntimeEventType.fromStoredCode(storedCode);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> buildPayload(RuntimeEventType eventType, String content) {
        if (eventType == RuntimeEventType.MESSAGE_DELTA) {
            return Map.of("content", content == null ? "" : content);
        }
        if (eventType == RuntimeEventType.RUN_PROGRESS) {
            return Map.of("message", content == null ? "" : content);
        }
        if (eventType != null) {
            return parsePayload(content);
        }
        if (eventType == null && content != null && !content.isBlank()) {
            return Map.of("content", content);
        }
        return Map.of();
    }

    private String resolveItemId(RuntimeEventType eventType, Map<String, Object> payload) {
        if (eventType == null) {
            return null;
        }
        if (eventType.code().startsWith("step.")) {
            Object taskStepId = payload.get("taskStepId");
            return taskStepId == null ? null : "STEP_" + taskStepId;
        }
        if (eventType.code().startsWith("tool.call.") || eventType == RuntimeEventType.POLICY_DECIDED) {
            Object toolCallId = payload.get("toolCallId");
            return toolCallId == null ? null : toolCallId.toString();
        }
        if (eventType.code().startsWith("approval.")) {
            Object approvalId = payload.get("approvalId");
            return approvalId == null ? null : "APPROVAL_" + approvalId;
        }
        if (eventType.code().startsWith("artifact.")) {
            Object artifactId = payload.get("artifactId");
            return artifactId == null ? null : artifactId.toString();
        }
        if (eventType.code().startsWith("checkpoint.")) {
            Object checkpointId = payload.get("checkpointId");
            return checkpointId == null ? null : "CHECKPOINT_" + checkpointId;
        }
        if (eventType.code().startsWith("plan.")) {
            Object planId = payload.get("planId");
            return planId == null ? null : planId.toString();
        }
        return null;
    }

    private Map<String, Object> parsePayload(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(objectMapper.readValue(
                    content,
                    new TypeReference<Map<String, Object>>() { }
            )));
        } catch (Exception ignored) {
            return Map.of("content", content);
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(
                    metadataJson,
                    new TypeReference<Map<String, Object>>() { }
            ));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * @param run 当前 Run 快照
     * @param events 当前批次事件
     */
    public record RuntimeEventBatch(AgentRunResponse run, List<RuntimeEventEnvelope> events) {
    }
}
