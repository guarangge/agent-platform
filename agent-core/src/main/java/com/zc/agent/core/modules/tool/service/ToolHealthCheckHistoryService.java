package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.model.TaskTraceAnalyticsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.tool.dto.ToolHealthCheckHistoryQueryRequest;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolHealthCheckMetricsRecord;
import com.zc.agent.core.modules.tool.model.ToolHealthCheckRecord;
import com.zc.agent.core.modules.tool.repository.ToolHealthCheckRepository;
import com.zc.agent.core.modules.tool.vo.ToolHealthCheckHistoryResponse;
import com.zc.agent.core.modules.tool.vo.ToolHealthCheckMetricsResponse;
import com.zc.agent.core.modules.tool.vo.ToolRelatedTraceResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Tool 健康检查历史服务。
 */
@Service
public class ToolHealthCheckHistoryService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int DEFAULT_CONSECUTIVE_FAILURE_ALERT_THRESHOLD = 3;
    private static final double DEFAULT_SLA_TARGET_SUCCESS_RATE = 99D;
    private static final int RELATED_TRACE_LIMIT = 5;

    private final ToolHealthCheckRepository toolHealthCheckRepository;
    private final RegistryAgentClient registryAgentClient;
    private final ConversationRepository conversationRepository;

    public ToolHealthCheckHistoryService(ToolHealthCheckRepository toolHealthCheckRepository,
                                         RegistryAgentClient registryAgentClient,
                                         ConversationRepository conversationRepository) {
        this.toolHealthCheckRepository = toolHealthCheckRepository;
        this.registryAgentClient = registryAgentClient;
        this.conversationRepository = conversationRepository;
    }

    public PageResponse<ToolHealthCheckHistoryResponse> page(Long toolId, ToolHealthCheckHistoryQueryRequest request) {
        int pageNum = request.pageNum() == null ? DEFAULT_PAGE_NUM : request.pageNum();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        long total = toolHealthCheckRepository.countByToolId(toolId);
        List<ToolHealthCheckHistoryResponse> records = toolHealthCheckRepository.pageByToolId(toolId, (pageNum - 1) * pageSize, pageSize).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public ToolHealthCheckMetricsResponse metrics(Long toolId, String authorization) {
        ToolHealthCheckMetricsRecord metrics = toolHealthCheckRepository.summarizeByToolId(toolId);
        RegistryAgentTool tool = registryAgentClient.getRuntimeToolDetail(toolId, authorization);
        long totalCount = metrics.totalCount() == null ? 0L : metrics.totalCount();
        long successCount = metrics.successCount() == null ? 0L : metrics.successCount();
        double successRate = totalCount == 0 ? 0D : ((double) successCount * 100D) / totalCount;
        int consecutiveFailureAlertThreshold = resolveConsecutiveFailureAlertThreshold(tool);
        int consecutiveFailureCount = resolveConsecutiveFailureCount(toolId);
        double slaTargetSuccessRate = resolveSlaTargetSuccessRate(tool);
        List<ToolRelatedTraceResponse> relatedTraces = conversationRepository.findRecentToolRelatedTraces(tool.toolCode(), RELATED_TRACE_LIMIT).stream()
                .map(this::toRelatedTraceResponse)
                .toList();
        return new ToolHealthCheckMetricsResponse(
                totalCount,
                successCount,
                metrics.failedCount() == null ? 0L : metrics.failedCount(),
                Math.round(successRate * 100D) / 100D,
                metrics.avgLatencyMs() == null ? 0L : metrics.avgLatencyMs(),
                metrics.lastCheckedAt(),
                metrics.lastSuccessAt(),
                toolHealthCheckRepository.findLatestFailureReasonByToolId(toolId).orElse(""),
                consecutiveFailureCount,
                consecutiveFailureAlertThreshold,
                consecutiveFailureCount >= consecutiveFailureAlertThreshold,
                slaTargetSuccessRate,
                totalCount > 0 && successRate < slaTargetSuccessRate,
                toolHealthCheckRepository.findLatestByToolId(toolId).map(this::toResponse).orElse(null),
                toolHealthCheckRepository.summarizeTrendByToolId(toolId, DEFAULT_TREND_DAYS).stream()
                        .map(item -> new ToolHealthCheckMetricsResponse.ToolHealthCheckTrendBucketResponse(
                                item.bucketKey(),
                                item.bucketLabel(),
                                item.totalCount(),
                                item.successCount(),
                                item.failedCount()
                        ))
                        .toList(),
                relatedTraces
        );
    }

    private ToolHealthCheckHistoryResponse toResponse(ToolHealthCheckRecord record) {
        return new ToolHealthCheckHistoryResponse(
                record.toolHealthCheckId(),
                record.toolId(),
                record.toolCode(),
                record.toolName(),
                record.providerType(),
                record.transportType(),
                record.providerCode(),
                record.providerName(),
                record.providerInstanceCode(),
                record.providerInstanceName(),
                record.environmentCode(),
                record.endpoint(),
                record.operationName(),
                record.operationType(),
                record.healthStatus(),
                record.success(),
                record.message(),
                record.failureReason(),
                record.statusCode(),
                record.latencyMs(),
                record.outputSnapshot(),
                record.checkedAt()
        );
    }

    private ToolRelatedTraceResponse toRelatedTraceResponse(TaskTraceAnalyticsRecord record) {
        return new ToolRelatedTraceResponse(
                record.conversationId(),
                record.conversationCode(),
                record.taskId(),
                record.taskCode(),
                record.traceId(),
                record.eventType(),
                record.spanName(),
                record.traceStatus(),
                record.errorMessage(),
                record.startedAt()
        );
    }

    private int resolveConsecutiveFailureCount(Long toolId) {
        List<ToolHealthCheckRecord> records = toolHealthCheckRepository.pageByToolId(toolId, 0, DEFAULT_CONSECUTIVE_FAILURE_ALERT_THRESHOLD + 10);
        int count = 0;
        for (ToolHealthCheckRecord record : records) {
            if (Boolean.TRUE.equals(record.success())) {
                break;
            }
            count++;
        }
        return count;
    }

    private int resolveConsecutiveFailureAlertThreshold(RegistryAgentTool tool) {
        Object configured = extractGovernanceConfig(tool).get("consecutiveFailureAlertThreshold");
        if (configured == null) {
            return DEFAULT_CONSECUTIVE_FAILURE_ALERT_THRESHOLD;
        }
        return Integer.parseInt(String.valueOf(configured));
    }

    private double resolveSlaTargetSuccessRate(RegistryAgentTool tool) {
        Object configured = extractGovernanceConfig(tool).get("slaTargetSuccessRate");
        if (configured == null) {
            return DEFAULT_SLA_TARGET_SUCCESS_RATE;
        }
        return Double.parseDouble(String.valueOf(configured));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractGovernanceConfig(RegistryAgentTool tool) {
        if (tool.metadata() == null || !tool.metadata().hasNonNull("toolGovernance")) {
            return Map.of();
        }
        Object value = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(tool.metadata().path("toolGovernance"), Map.class);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
