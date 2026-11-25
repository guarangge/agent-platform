package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.ApprovalWorkbenchQueryRequest;
import com.zc.agent.core.modules.conversation.model.ApprovalWorkbenchRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.ApprovalWorkbenchResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 审批工作台服务。
 */
@Service
public class ApprovalWorkbenchService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public ApprovalWorkbenchService(ConversationRepository conversationRepository,
                                    ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ApprovalWorkbenchResponse> page(ApprovalWorkbenchQueryRequest request,
                                                        SecurityUser securityUser) {
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        String status = request.status() == null || request.status().isBlank() ? "pending" : request.status().trim();
        List<ApprovalWorkbenchResponse> matched = conversationRepository.findApprovalWorkbenchRecords(
                        status,
                        request.agentId(),
                        request.conversationCode(),
                        request.taskCode()
                ).stream()
                .filter(item -> matchesAssignee(item, securityUser))
                .map(this::toResponse)
                .toList();
        int fromIndex = Math.min((pageNum - 1) * pageSize, matched.size());
        int toIndex = Math.min(fromIndex + pageSize, matched.size());
        return new PageResponse<>(matched.subList(fromIndex, toIndex), matched.size(), pageNum, pageSize);
    }

    private boolean matchesAssignee(ApprovalWorkbenchRecord record, SecurityUser securityUser) {
        try {
            JsonNode metadata = objectMapper.readTree(record.metadata() == null ? "{}" : record.metadata());
            JsonNode approvalUserIds = metadata.path("approvalUserIds");
            JsonNode approvalRoleCodes = metadata.path("approvalRoleCodes");
            boolean hasUserAssignments = approvalUserIds.isArray() && !approvalUserIds.isEmpty();
            boolean hasRoleAssignments = approvalRoleCodes.isArray() && !approvalRoleCodes.isEmpty();
            if (!hasUserAssignments && !hasRoleAssignments) {
                return true;
            }
            if (hasUserAssignments) {
                for (JsonNode item : approvalUserIds) {
                    if (String.valueOf(securityUser.userId()).equals(item.asText())) {
                        return true;
                    }
                }
            }
            if (hasRoleAssignments && securityUser.roleCodes() != null) {
                for (JsonNode item : approvalRoleCodes) {
                    if (securityUser.roleCodes().contains(item.asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private ApprovalWorkbenchResponse toResponse(ApprovalWorkbenchRecord record) {
        JsonNode metadata = parseMetadata(record.metadata());
        List<Long> approvalUserIds = jsonLongList(metadata.path("approvalUserIds"));
        List<String> approvalRoleCodes = jsonTextList(metadata.path("approvalRoleCodes"));
        Integer timeoutMinutes = resolveTimeoutMinutes(metadata);
        LocalDateTime deadlineAt = record.createdAt() == null || timeoutMinutes == null
                ? null
                : record.createdAt().plusMinutes(timeoutMinutes);
        Long waitingMinutes = record.createdAt() == null
                ? null
                : Math.max(0L, ChronoUnit.MINUTES.between(record.createdAt(), LocalDateTime.now()));
        return new ApprovalWorkbenchResponse(
                record.approvalId(),
                record.conversationId(),
                record.conversationCode(),
                record.taskId(),
                record.taskCode(),
                record.agentId(),
                record.agentName(),
                record.taskStepId(),
                record.nodeId(),
                record.nodeName(),
                record.status(),
                record.approvalComment(),
                record.approvedBy(),
                record.approvedAt(),
                approvalUserIds,
                approvalRoleCodes,
                timeoutMinutes,
                deadlineAt,
                waitingMinutes,
                textValue(metadata, "approvalSourceType"),
                integerValue(metadata, "nextPlanStepNo"),
                record.metadata(),
                record.createdAt()
        );
    }

    private JsonNode parseMetadata(String metadataText) {
        try {
            return objectMapper.readTree(metadataText == null ? "{}" : metadataText);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private List<Long> jsonLongList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::canConvertToLong)
                .map(JsonNode::asLong)
                .toList();
    }

    private List<String> jsonTextList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private Integer resolveTimeoutMinutes(JsonNode metadata) {
        int timeoutMinutes = integerValue(metadata, "timeoutMinutes") == null ? 0 : integerValue(metadata, "timeoutMinutes");
        if (timeoutMinutes > 0) {
            return timeoutMinutes;
        }
        JsonNode primaryPolicy = metadata.path("primaryPolicy");
        int primaryPolicyTimeout = primaryPolicy.path("timeoutMinutes").asInt(0);
        return primaryPolicyTimeout > 0 ? primaryPolicyTimeout : null;
    }

    private Integer integerValue(JsonNode jsonNode, String fieldName) {
        if (!jsonNode.hasNonNull(fieldName)) {
            return null;
        }
        int value = jsonNode.path(fieldName).asInt(Integer.MIN_VALUE);
        return value == Integer.MIN_VALUE ? null : value;
    }

    private String textValue(JsonNode jsonNode, String fieldName) {
        if (!jsonNode.hasNonNull(fieldName)) {
            return null;
        }
        String value = jsonNode.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
