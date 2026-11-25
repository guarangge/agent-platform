package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.GuardrailPolicyHitPageQueryRequest;
import com.zc.agent.core.modules.conversation.model.GuardrailPolicyHitRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.GuardrailPolicyHitResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Guardrail 策略命中服务。
 */
@Service
public class GuardrailPolicyHitService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public GuardrailPolicyHitService(ConversationRepository conversationRepository,
                                     ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    public void recordHit(Long conversationId,
                          Long taskId,
                          Long taskStepId,
                          Long agentId,
                          String traceId,
                          String decisionType,
                          Map<String, Object> policyMetadata,
                          Long operatorId) {
        if (policyMetadata == null || policyMetadata.isEmpty()) {
            return;
        }
        Map<String, Object> primaryPolicy = map(policyMetadata.get("primaryPolicy"));
        conversationRepository.insertGuardrailPolicyHit(
                conversationId,
                taskId,
                taskStepId,
                agentId,
                traceId,
                string(primaryPolicy.get("policyCode")),
                string(primaryPolicy.get("policyName")),
                integer(primaryPolicy.get("version")),
                decisionType,
                string(policyMetadata.get("resourceKind")),
                longValue(policyMetadata.get("resourceId")),
                string(policyMetadata.get("resourceCode")),
                string(policyMetadata.get("resourceName")),
                string(primaryPolicy.get("hitDetail")),
                toJsonText(policyMetadata),
                operatorId
        );
    }

    public PageResponse<GuardrailPolicyHitResponse> pageHits(GuardrailPolicyHitPageQueryRequest request,
                                                             SecurityUser securityUser) {
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 10 : request.pageSize();
        long total = conversationRepository.countGuardrailPolicyHits(
                securityUser.userId(),
                normalize(request.policyCode()),
                normalize(request.decisionType()),
                normalize(request.traceId()),
                request.taskId(),
                request.agentId(),
                request.startedFrom(),
                request.startedTo()
        );
        List<GuardrailPolicyHitResponse> records = conversationRepository.pageGuardrailPolicyHits(
                        securityUser.userId(),
                        normalize(request.policyCode()),
                        normalize(request.decisionType()),
                        normalize(request.traceId()),
                        request.taskId(),
                        request.agentId(),
                        request.startedFrom(),
                        request.startedTo(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    private GuardrailPolicyHitResponse toResponse(GuardrailPolicyHitRecord record) {
        return new GuardrailPolicyHitResponse(
                record.hitId(),
                record.conversationId(),
                record.taskId(),
                record.taskStepId(),
                record.agentId(),
                record.traceId(),
                record.policyCode(),
                record.policyName(),
                record.policyVersion(),
                record.decisionType(),
                record.resourceKind(),
                record.resourceId(),
                record.resourceCode(),
                record.resourceName(),
                record.hitDetail(),
                record.metadata(),
                record.createdAt()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("策略命中记录序列化失败");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
