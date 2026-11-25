package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 统一维护 Approval 状态与 Runtime Event，不将审批控制状态写入聊天消息。
 */
@Service
public class ApprovalRuntimeEventService {

    private static final Pattern TOOL_CALL_ID_PATTERN = Pattern.compile("TOOL_CALL_[A-F0-9]{20}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private final ConversationRepository conversationRepository;
    private final AgentRunQueueRepository runRepository;
    private final RuntimeEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ApprovalRuntimeEventService(ConversationRepository conversationRepository,
                                       AgentRunQueueRepository runRepository,
                                       RuntimeEventPublisher eventPublisher,
                                       ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /** 创建待审批记录，并在同一事务内发布 approval.requested。 */
    @Transactional
    public Long request(RequestApprovalCommand command) {
        Long approvalId = conversationRepository.insertApproval(
                command.conversationId(), command.taskId(), command.taskStepId(), command.nodeId(),
                command.nodeName(), command.metadataJson(), command.operatorId());
        resolveRunCode(command.taskId()).ifPresent(runCode -> eventPublisher.publishPayload(
                runCode,
                RuntimeEventType.APPROVAL_REQUESTED,
                payload(approvalId, command.taskId(), command.taskStepId(), command.nodeId(),
                        command.nodeName(), command.approvalSourceType(), "pending", null,
                        command.metadataJson()),
                traceMetadata(command.traceId())
        ));
        return approvalId;
    }

    /** 更新审批结果，并在同一事务内发布 resolved 或 revoked 事件。 */
    @Transactional
    public void transition(TransitionApprovalCommand command) {
        RuntimeEventType eventType = resolveEventType(command.status());
        conversationRepository.updateApprovalStatus(
                command.approval().approvalId(), command.status(), command.approvalComment(), command.operatorId());
        resolveRunCode(command.approval().taskId()).ifPresent(runCode -> eventPublisher.publishPayload(
                runCode,
                eventType,
                payload(command.approval().approvalId(), command.approval().taskId(),
                        command.approval().taskStepId(), command.approval().nodeId(),
                        command.approval().nodeName(), command.approvalSourceType(),
                        command.status(), command.operatorId(), command.approval().metadata()),
                traceMetadata(command.traceId())
        ));
    }

    /** 审批通过续跑前恢复原 Run 上下文，使后续 Step/Tool 事件仍关联同一 Run。 */
    public AgentRunStreamingContext.Scope openRunScope(Long taskId) {
        return runRepository.findRunCodeByTaskId(taskId)
                .<AgentRunStreamingContext.Scope>map(AgentRunStreamingContext::openRunScope)
                .orElse(() -> { });
    }

    private RuntimeEventType resolveEventType(String status) {
        return switch (status) {
            case "approved", "rejected", "timed_out" -> RuntimeEventType.APPROVAL_RESOLVED;
            case "revoked" -> RuntimeEventType.APPROVAL_REVOKED;
            default -> throw new IllegalArgumentException("不支持的 Approval Runtime Event 状态: " + status);
        };
    }

    private java.util.Optional<String> resolveRunCode(Long taskId) {
        String currentRunCode = AgentRunStreamingContext.currentRunCode();
        if (currentRunCode != null && !currentRunCode.isBlank()) {
            return java.util.Optional.of(currentRunCode);
        }
        return runRepository.findRunCodeByTaskId(taskId);
    }

    private Map<String, Object> payload(Long approvalId,
                                        Long taskId,
                                        Long taskStepId,
                                        String nodeId,
                                        String nodeName,
                                        String approvalSourceType,
                                        String status,
                                        Long resolvedBy,
                                        String metadataJson) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", approvalId);
        payload.put("taskId", taskId);
        payload.put("taskStepId", taskStepId);
        payload.put("nodeId", safe(nodeId));
        payload.put("nodeName", safe(nodeName));
        payload.put("approvalSourceType", safe(approvalSourceType));
        payload.put("status", status);
        if (resolvedBy != null) {
            payload.put("resolvedBy", resolvedBy);
        }
        appendSafeCapabilityIdentity(payload, metadataJson);
        return payload;
    }

    private void appendSafeCapabilityIdentity(Map<String, Object> payload, String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return;
        }
        try {
            JsonNode metadata = objectMapper.readTree(metadataJson);
            if (!"capability".equals(metadata.path("policyType").asText())) {
                return;
            }
            String toolCallId = metadata.path("toolCallId").asText("");
            String parameterHash = metadata.path("parameterHash").asText("");
            if (TOOL_CALL_ID_PATTERN.matcher(toolCallId).matches()) {
                payload.put("toolCallId", toolCallId);
            }
            if (SHA256_PATTERN.matcher(parameterHash).matches()) {
                payload.put("parameterHash", parameterHash);
            }
        } catch (Exception ignored) {
            // 审批元数据仍由审批表保存；事件投影解析失败不能扩大或泄露原始内容。
        }
    }

    private Map<String, Object> traceMetadata(String traceId) {
        return traceId == null || traceId.isBlank() ? Map.of() : Map.of("traceId", traceId);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param conversationId 会话 ID
     * @param taskId Task ID
     * @param taskStepId Task Step ID
     * @param nodeId 审批节点 ID
     * @param nodeName 审批节点名称
     * @param approvalSourceType 审批来源类型
     * @param metadataJson 审批恢复元数据 JSON
     * @param traceId Trace ID
     * @param operatorId 操作人 ID
     */
    public record RequestApprovalCommand(
            Long conversationId,
            Long taskId,
            Long taskStepId,
            String nodeId,
            String nodeName,
            String approvalSourceType,
            String metadataJson,
            String traceId,
            Long operatorId
    ) {
    }

    /**
     * @param approval 审批记录
     * @param status 目标状态
     * @param approvalComment 审批说明，仅保存审批表，不进入 Runtime Event
     * @param approvalSourceType 审批来源类型
     * @param traceId Trace ID
     * @param operatorId 操作人 ID
     */
    public record TransitionApprovalCommand(
            ApprovalRecord approval,
            String status,
            String approvalComment,
            String approvalSourceType,
            String traceId,
            Long operatorId
    ) {
    }
}
