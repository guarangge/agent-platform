package com.zc.agent.core.modules.runtime.capability.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.runtime.capability.dto.CapabilityPolicyAuditQueryRequest;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditBucketRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditMetricsRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditRecord;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditScope;
import com.zc.agent.core.modules.runtime.capability.model.RuntimeCapability;
import com.zc.agent.core.modules.runtime.capability.model.ToolSideEffectLevel;
import com.zc.agent.core.modules.runtime.capability.repository.CapabilityPolicyAuditRepository;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditDetailResponse;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditItemResponse;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditMetricsResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Capability Policy 全量决策运营查询服务。 */
@Service
public class CapabilityPolicyAuditService {

    private static final Set<String> DECISIONS = Set.of("allow", "sandbox", "approval", "block");
    private final CapabilityPolicyAuditRepository repository;
    private final ObjectMapper objectMapper;

    public CapabilityPolicyAuditService(CapabilityPolicyAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 分页查询当前用户可见的全部 Capability Policy 决策。 */
    public PageResponse<CapabilityPolicyAuditItemResponse> page(CapabilityPolicyAuditQueryRequest request,
                                                                 SecurityUser securityUser) {
        CapabilityPolicyAuditQueryRequest normalized = normalizeAndValidate(request);
        int pageNum = normalized.pageNum() == null ? 1 : normalized.pageNum();
        int pageSize = normalized.pageSize() == null ? 20 : normalized.pageSize();
        CapabilityPolicyAuditScope scope = requireScope(securityUser);
        long total = repository.count(scope, normalized);
        List<CapabilityPolicyAuditItemResponse> records = repository.page(
                        scope, normalized, (pageNum - 1) * pageSize, pageSize)
                .stream().map(this::toItem).toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /** 汇总当前筛选范围内的决策、能力和 Tool 分布。 */
    public CapabilityPolicyAuditMetricsResponse metrics(CapabilityPolicyAuditQueryRequest request,
                                                         SecurityUser securityUser) {
        CapabilityPolicyAuditQueryRequest normalized = normalizeAndValidate(request);
        CapabilityPolicyAuditScope scope = requireScope(securityUser);
        CapabilityPolicyAuditMetricsRecord metrics = repository.metrics(scope, normalized);
        return new CapabilityPolicyAuditMetricsResponse(
                value(metrics.totalCount()), value(metrics.allowCount()), value(metrics.sandboxCount()),
                value(metrics.approvalCount()), value(metrics.blockCount()), value(metrics.approvedReplayCount()),
                value(metrics.runCount()), value(metrics.toolCount()),
                buckets(repository.decisionBuckets(scope, normalized)),
                buckets(repository.capabilityBuckets(scope, normalized)),
                buckets(repository.toolBuckets(scope, normalized))
        );
    }

    /** 查询单次决策及同一 Tool Call 的策略、审批和执行事件链。 */
    public CapabilityPolicyAuditDetailResponse detail(Long eventId, SecurityUser securityUser) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("Capability Policy 审计事件 ID 无效");
        }
        CapabilityPolicyAuditScope scope = requireScope(securityUser);
        CapabilityPolicyAuditRecord record = repository.findByEventId(scope, eventId)
                .orElseThrow(() -> new BusinessException("Capability Policy 审计记录不存在"));
        List<CapabilityPolicyAuditDetailResponse.CorrelatedEvent> correlatedEvents =
                record.toolCallId() == null || record.toolCallId().isBlank()
                        ? List.of()
                        : repository.findCorrelatedEvents(scope, record.runCode(), record.toolCallId()).stream()
                        .map(event -> new CapabilityPolicyAuditDetailResponse.CorrelatedEvent(
                                "EVT_" + event.eventId(), event.sequenceNo(), event.eventType(), event.payloadVersion(),
                                readMap(event.content()), readMap(event.metadata()), event.createdAt()
                        )).toList();
        return new CapabilityPolicyAuditDetailResponse(toItem(record), correlatedEvents);
    }

    private CapabilityPolicyAuditQueryRequest normalizeAndValidate(CapabilityPolicyAuditQueryRequest request) {
        String decision = normalize(request.decision());
        String toolCode = normalize(request.toolCode());
        String capability = normalize(request.capability());
        String sideEffectLevel = normalize(request.sideEffectLevel());
        String policyCode = normalize(request.policyCode());
        String runCode = normalize(request.runCode());
        String traceId = normalize(request.traceId());
        if (decision != null && !DECISIONS.contains(decision)) {
            throw new BusinessException("Capability Policy 决策动作无效: " + decision);
        }
        if (capability != null) {
            try {
                RuntimeCapability.normalize(List.of(capability));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(exception.getMessage());
            }
        }
        if (sideEffectLevel != null) {
            try {
                ToolSideEffectLevel.require(sideEffectLevel);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(exception.getMessage());
            }
        }
        if (request.occurredFrom() != null && request.occurredTo() != null
                && request.occurredFrom().isAfter(request.occurredTo())) {
            throw new BusinessException("Capability Policy 查询开始时间不能晚于结束时间");
        }
        return new CapabilityPolicyAuditQueryRequest(
                request.pageNum(), request.pageSize(), decision, toolCode, capability, sideEffectLevel,
                policyCode, request.agentId(), runCode, traceId, request.occurredFrom(), request.occurredTo());
    }

    private CapabilityPolicyAuditItemResponse toItem(CapabilityPolicyAuditRecord record) {
        return new CapabilityPolicyAuditItemResponse(
                record.eventId(), record.sequenceNo(), record.runCode(), record.conversationId(),
                record.conversationCode(), record.taskId(), record.taskCode(), record.agentId(), record.agentName(),
                record.traceId(), record.toolCallId(), record.toolCode(), record.toolName(), record.decision(),
                record.requestedAction(), record.policyCode(), record.policyVersion(), readStringList(record.runtimeCapabilitiesJson()),
                record.sideEffectLevel(), record.parameterHash(), record.targetHash(), record.approvalNodeId(),
                Boolean.TRUE.equals(record.approvalGranted()), record.createdAt()
        );
    }

    private List<CapabilityPolicyAuditMetricsResponse.Bucket> buckets(
            List<CapabilityPolicyAuditBucketRecord> records) {
        return records.stream().map(record -> new CapabilityPolicyAuditMetricsResponse.Bucket(
                record.key(), record.label(), value(record.count()))).toList();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BusinessException("Capability Policy 能力审计快照解析失败");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BusinessException("Capability Policy 关联事件解析失败");
        }
    }

    private CapabilityPolicyAuditScope requireScope(SecurityUser securityUser) {
        if (securityUser == null || securityUser.userId() == null || securityUser.tenantId() == null
                || securityUser.organizationId() == null || securityUser.workspaceId() == null
                || securityUser.projectId() == null) {
            throw new BusinessException("当前用户身份无效");
        }
        return new CapabilityPolicyAuditScope(
                securityUser.userId(), securityUser.tenantId(), securityUser.organizationId(),
                securityUser.workspaceId(), securityUser.projectId());
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
