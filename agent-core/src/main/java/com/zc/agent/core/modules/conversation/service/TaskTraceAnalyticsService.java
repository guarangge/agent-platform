package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.TaskTraceQueryRequest;
import com.zc.agent.core.modules.conversation.model.TaskTraceAnalyticsRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceMetricBucketRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceMetricsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.TaskTraceAnalyticsResponse;
import com.zc.agent.core.modules.conversation.vo.CompactionMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.CompactionTraceResponse;
import com.zc.agent.core.modules.conversation.vo.TaskTraceMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.TraceTopologyResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Trace 运营分析服务。
 */
@Service
public class TaskTraceAnalyticsService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EXPORT_LIMIT = 10_000;
    private static final int SLOW_SPAN_LIMIT = 10;

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public TaskTraceAnalyticsService(ConversationRepository conversationRepository,
                                     ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<TaskTraceAnalyticsResponse> page(TaskTraceQueryRequest request, SecurityUser securityUser) {
        int pageNum = request.pageNum() == null ? DEFAULT_PAGE_NUM : request.pageNum();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        long total = conversationRepository.countTaskTraceAnalytics(
                securityUser.userId(),
                normalize(request.traceId()),
                normalize(request.eventType()),
                normalize(request.traceStatus()),
                request.agentId(),
                request.conversationId(),
                request.taskId(),
                normalize(request.keyword()),
                request.startedFrom(),
                request.startedTo()
        );
        List<TaskTraceAnalyticsResponse> records = conversationRepository.pageTaskTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public TaskTraceMetricsResponse metrics(TaskTraceQueryRequest request, SecurityUser securityUser) {
        TaskTraceMetricsRecord overview = conversationRepository.summarizeTaskTraceMetrics(
                securityUser.userId(),
                normalize(request.traceId()),
                normalize(request.eventType()),
                normalize(request.traceStatus()),
                request.agentId(),
                request.conversationId(),
                request.taskId(),
                normalize(request.keyword()),
                request.startedFrom(),
                request.startedTo()
        );
        long totalCount = overview.totalCount() == null ? 0L : overview.totalCount();
        long failedCount = overview.failedCount() == null ? 0L : overview.failedCount();
        double failureRate = totalCount == 0 ? 0D : ((double) failedCount * 100D) / totalCount;
        List<TaskTraceAnalyticsResponse> compactionSource = conversationRepository.pageTaskTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        0,
                        EXPORT_LIMIT
                ).stream()
                .map(this::toResponse)
                .toList();
        CompactionMetricSummary compactionMetricSummary = summarizeCompactionMetrics(compactionSource);
        return new TaskTraceMetricsResponse(
                totalCount,
                overview.traceCount() == null ? 0L : overview.traceCount(),
                failedCount,
                Math.round(failureRate * 100D) / 100D,
                overview.avgDurationMs() == null ? 0L : overview.avgDurationMs(),
                conversationRepository.findApprovalAverageWaitMs(
                        securityUser.userId(),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        request.startedFrom(),
                        request.startedTo()
                ),
                conversationRepository.findApprovalMaxWaitMs(
                        securityUser.userId(),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        request.startedFrom(),
                        request.startedTo()
                ),
                compactionMetricSummary.observedCount(),
                compactionMetricSummary.criticalCount(),
                compactionMetricSummary.avgQualityScore(),
                compactionMetricSummary.avgSavedTokens(),
                toBucketResponses(conversationRepository.summarizeTaskTraceByAgent(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo()
                )),
                toBucketResponses(conversationRepository.summarizeTaskTraceByStatus(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo()
                )),
                toBucketResponses(conversationRepository.summarizeTaskTraceByEventType(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo()
                )),
                conversationRepository.findSlowTaskTraceSpans(
                                securityUser.userId(),
                                normalize(request.traceId()),
                                normalize(request.eventType()),
                                normalize(request.traceStatus()),
                                request.agentId(),
                                request.conversationId(),
                                request.taskId(),
                                normalize(request.keyword()),
                                request.startedFrom(),
                                request.startedTo(),
                                SLOW_SPAN_LIMIT
                        ).stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    public List<TaskTraceAnalyticsResponse> export(TaskTraceQueryRequest request, SecurityUser securityUser) {
        return conversationRepository.pageTaskTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        0,
                        EXPORT_LIMIT
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    public TraceTopologyResponse topology(Long conversationId,
                                          Long taskId,
                                          String traceId,
                                          SecurityUser securityUser) {
        String normalizedTraceId = normalize(traceId);
        List<TaskTraceAnalyticsResponse> records = conversationRepository.pageTaskTraceAnalytics(
                        securityUser.userId(),
                        normalizedTraceId,
                        null,
                        null,
                        null,
                        conversationId,
                        taskId,
                        null,
                        null,
                        null,
                        0,
                        EXPORT_LIMIT
                ).stream()
                .map(this::toResponse)
                .sorted(Comparator
                        .comparing(TaskTraceAnalyticsResponse::startedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(TaskTraceAnalyticsResponse::createdAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(TaskTraceAnalyticsResponse::taskTraceId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<TraceTopologyResponse.TraceApprovalNode> approvals = conversationRepository.findApprovalsByTaskId(taskId).stream()
                .map(item -> new TraceTopologyResponse.TraceApprovalNode(
                        item.approvalId(),
                        item.taskStepId(),
                        item.nodeId(),
                        item.nodeName(),
                        item.status(),
                        item.approvalComment(),
                        item.approvedBy(),
                        item.approvedAt(),
                        item.metadata(),
                        item.createdAt()
                ))
                .toList();
        List<TraceTopologyResponse.TraceDelegationNode> delegations = conversationRepository.findTaskDelegationsByConversationId(conversationId).stream()
                .filter(item -> normalizedTraceId == null || Objects.equals(normalizedTraceId, item.traceId()))
                .map(item -> new TraceTopologyResponse.TraceDelegationNode(
                        item.delegationId(),
                        item.parentTaskId(),
                        item.parentTaskStepId(),
                        item.childTaskId(),
                        item.delegateAgentId(),
                        item.delegateAgentCode(),
                        item.delegateAgentName(),
                        item.status(),
                        item.traceId(),
                        item.inputSnapshot(),
                        item.outputSnapshot(),
                        item.errorMessage(),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();
        return new TraceTopologyResponse(
                conversationId,
                taskId,
                normalizedTraceId,
                buildTopologySummary(records, approvals, delegations),
                records.stream()
                        .map(item -> new TraceTopologyResponse.TraceSpanNode(
                                item.taskTraceId(),
                                item.taskStepId(),
                                item.eventType(),
                                item.spanName(),
                                item.traceStatus(),
                                item.durationMs(),
                                item.errorMessage(),
                                item.inputSnapshot(),
                                item.outputSnapshot(),
                                item.metadata(),
                                item.startedAt(),
                                item.finishedAt()
                        ))
                        .toList(),
                approvals,
                delegations
        );
    }

    public PageResponse<CompactionTraceResponse> pageCompactions(TaskTraceQueryRequest request, SecurityUser securityUser) {
        int pageNum = request.pageNum() == null ? DEFAULT_PAGE_NUM : request.pageNum();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        long total = conversationRepository.countCompactionTraceAnalytics(
                securityUser.userId(),
                normalize(request.traceId()),
                normalize(request.eventType()),
                normalize(request.traceStatus()),
                request.agentId(),
                request.conversationId(),
                request.taskId(),
                normalize(request.keyword()),
                request.startedFrom(),
                request.startedTo()
        );
        List<CompactionTraceResponse> records = conversationRepository.pageCompactionTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toCompactionResponse)
                .filter(Objects::nonNull)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public CompactionMetricsResponse compactionMetrics(TaskTraceQueryRequest request, SecurityUser securityUser) {
        List<CompactionTraceResponse> records = conversationRepository.pageCompactionTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        0,
                        EXPORT_LIMIT
                ).stream()
                .map(this::toCompactionResponse)
                .filter(Objects::nonNull)
                .toList();
        long totalCount = records.size();
        long criticalCount = records.stream().filter(item -> "critical".equalsIgnoreCase(item.qualityStatus())).count();
        long warningCount = records.stream().filter(item -> "warning".equalsIgnoreCase(item.qualityStatus())).count();
        double avgQualityScore = totalCount == 0
                ? 0D
                : Math.round(records.stream().map(CompactionTraceResponse::qualityScore).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D) * 100D) / 100D;
        long avgSavedChars = totalCount == 0
                ? 0L
                : Math.round(records.stream().map(CompactionTraceResponse::savedChars).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
        long avgSavedTokens = totalCount == 0
                ? 0L
                : Math.round(records.stream().map(CompactionTraceResponse::savedTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
        return new CompactionMetricsResponse(
                totalCount,
                criticalCount,
                warningCount,
                avgQualityScore,
                avgSavedChars,
                avgSavedTokens,
                bucketBy(records, CompactionTraceResponse::stage),
                bucketBy(records, CompactionTraceResponse::qualityStatus),
                records.stream()
                        .sorted(Comparator.comparing(CompactionTraceResponse::savedTokens, Comparator.nullsLast(Integer::compareTo)).reversed())
                        .limit(10)
                        .toList()
        );
    }

    public List<CompactionTraceResponse> exportCompactions(TaskTraceQueryRequest request, SecurityUser securityUser) {
        return conversationRepository.pageCompactionTraceAnalytics(
                        securityUser.userId(),
                        normalize(request.traceId()),
                        normalize(request.eventType()),
                        normalize(request.traceStatus()),
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        normalize(request.keyword()),
                        request.startedFrom(),
                        request.startedTo(),
                        0,
                        EXPORT_LIMIT
                ).stream()
                .map(this::toCompactionResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    private TraceTopologyResponse.TraceTopologySummary buildTopologySummary(List<TaskTraceAnalyticsResponse> spans,
                                                                           List<TraceTopologyResponse.TraceApprovalNode> approvals,
                                                                           List<TraceTopologyResponse.TraceDelegationNode> delegations) {
        int successSpanCount = (int) spans.stream().filter(item -> "success".equals(item.traceStatus())).count();
        int failedSpanCount = (int) spans.stream().filter(item -> "failed".equals(item.traceStatus())).count();
        int waitingApprovalSpanCount = (int) spans.stream().filter(item -> "waiting_approval".equals(item.traceStatus())).count();
        int pendingApprovalCount = (int) approvals.stream().filter(item -> "pending".equals(item.status())).count();
        int approvedApprovalCount = (int) approvals.stream().filter(item -> "approved".equals(item.status())).count();
        int rejectedApprovalCount = (int) approvals.stream().filter(item -> "rejected".equals(item.status())).count();
        int timedOutApprovalCount = (int) approvals.stream().filter(item -> "timed_out".equals(item.status())).count();
        int revokedApprovalCount = (int) approvals.stream().filter(item -> "revoked".equals(item.status())).count();
        int successDelegationCount = (int) delegations.stream().filter(item -> "success".equals(item.status())).count();
        int failedDelegationCount = (int) delegations.stream().filter(item -> "failed".equals(item.status())).count();
        int processingDelegationCount = (int) delegations.stream().filter(item -> "processing".equals(item.status())).count();
        long totalDurationMs = spans.stream()
                .map(TaskTraceAnalyticsResponse::durationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        LocalDateTime firstStartedAt = spans.stream()
                .map(TaskTraceAnalyticsResponse::startedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastFinishedAt = spans.stream()
                .map(item -> item.finishedAt() == null ? item.startedAt() : item.finishedAt())
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Long wallClockDurationMs = firstStartedAt == null || lastFinishedAt == null
                ? null
                : Math.max(0L, ChronoUnit.MILLIS.between(firstStartedAt, lastFinishedAt));
        return new TraceTopologyResponse.TraceTopologySummary(
                spans.size(),
                successSpanCount,
                failedSpanCount,
                waitingApprovalSpanCount,
                approvals.size(),
                pendingApprovalCount,
                approvedApprovalCount,
                rejectedApprovalCount,
                timedOutApprovalCount,
                revokedApprovalCount,
                delegations.size(),
                successDelegationCount,
                failedDelegationCount,
                processingDelegationCount,
                totalDurationMs,
                wallClockDurationMs
        );
    }

    private List<TaskTraceMetricsResponse.TraceMetricBucketResponse> toBucketResponses(List<TaskTraceMetricBucketRecord> records) {
        return records.stream()
                .map(item -> new TaskTraceMetricsResponse.TraceMetricBucketResponse(item.bucketKey(), item.bucketLabel(), item.countValue()))
                .toList();
    }

    private TaskTraceAnalyticsResponse toResponse(TaskTraceAnalyticsRecord record) {
        return new TaskTraceAnalyticsResponse(
                record.taskTraceId(),
                record.conversationId(),
                record.conversationCode(),
                record.taskId(),
                record.taskCode(),
                record.agentId(),
                record.agentName(),
                record.traceId(),
                record.taskStepId(),
                record.stepNo(),
                record.stepName(),
                record.eventType(),
                record.spanName(),
                record.traceStatus(),
                record.durationMs(),
                record.errorMessage(),
                record.inputSnapshot(),
                record.outputSnapshot(),
                record.metadata(),
                record.startedAt(),
                record.finishedAt(),
                record.createdAt()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CompactionTraceResponse toCompactionResponse(TaskTraceAnalyticsRecord record) {
        try {
            JsonNode metadata = objectMapper.readTree(record.metadata());
            JsonNode compaction = metadata == null ? null : metadata.get("contextCompaction");
            if (compaction == null || !compaction.isObject()) {
                return null;
            }
            return new CompactionTraceResponse(
                    record.taskTraceId(),
                    record.conversationId(),
                    record.conversationCode(),
                    record.taskId(),
                    record.taskCode(),
                    record.agentId(),
                    record.agentName(),
                    record.traceId(),
                    record.taskStepId(),
                    record.stepNo(),
                    record.stepName(),
                    record.eventType(),
                    record.spanName(),
                    record.traceStatus(),
                    record.durationMs(),
                    record.errorMessage(),
                    readText(compaction, "stage"),
                    readText(compaction, "tokenizerFamily"),
                    readInt(compaction, "qualityScore"),
                    readText(compaction, "qualityStatus"),
                    readInt(compaction, "originalChars"),
                    readInt(compaction, "compactedChars"),
                    readInt(compaction, "savedChars"),
                    readInt(compaction, "estimatedOriginalTokens"),
                    readInt(compaction, "estimatedCompactedTokens"),
                    readInt(compaction, "savedTokens"),
                    readBoolean(compaction, "summarized"),
                    readBoolean(compaction, "truncated"),
                    toJsonText(compaction.get("policy")),
                    record.metadata(),
                    record.startedAt(),
                    record.finishedAt(),
                    record.createdAt()
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private List<CompactionMetricsResponse.BucketItem> bucketBy(List<CompactionTraceResponse> records,
                                                                java.util.function.Function<CompactionTraceResponse, String> classifier) {
        LinkedHashMap<String, Long> countMap = new LinkedHashMap<>();
        for (CompactionTraceResponse record : records) {
            String key = classifier.apply(record);
            String normalized = key == null || key.isBlank() ? "unknown" : key;
            countMap.put(normalized, countMap.getOrDefault(normalized, 0L) + 1L);
        }
        ArrayList<CompactionMetricsResponse.BucketItem> result = new ArrayList<>();
        countMap.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .forEach(entry -> result.add(new CompactionMetricsResponse.BucketItem(entry.getKey(), entry.getKey(), entry.getValue())));
        return result;
    }

    private String toJsonText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer readInt(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isNumber() ? value.intValue() : null;
    }

    private Boolean readBoolean(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }

    private CompactionMetricSummary summarizeCompactionMetrics(List<TaskTraceAnalyticsResponse> records) {
        if (records == null || records.isEmpty()) {
            return new CompactionMetricSummary(0L, 0L, 0D, 0L);
        }
        long observedCount = 0L;
        long criticalCount = 0L;
        long totalSavedTokens = 0L;
        double totalQualityScore = 0D;
        for (TaskTraceAnalyticsResponse record : records) {
            JsonNode contextCompaction = parseContextCompaction(record.metadata());
            if (contextCompaction == null || !contextCompaction.isObject()) {
                continue;
            }
            observedCount++;
            if ("critical".equalsIgnoreCase(contextCompaction.path("qualityStatus").asText())) {
                criticalCount++;
            }
            totalSavedTokens += contextCompaction.path("savedTokens").asLong(0L);
            totalQualityScore += contextCompaction.path("qualityScore").asDouble(0D);
        }
        if (observedCount <= 0L) {
            return new CompactionMetricSummary(0L, 0L, 0D, 0L);
        }
        double avgQualityScore = Math.round((totalQualityScore / observedCount) * 100D) / 100D;
        long avgSavedTokens = Math.round((double) totalSavedTokens / observedCount);
        return new CompactionMetricSummary(observedCount, criticalCount, avgQualityScore, avgSavedTokens);
    }

    private JsonNode parseContextCompaction(String metadataText) {
        if (metadataText == null || metadataText.isBlank()) {
            return null;
        }
        try {
            JsonNode metadata = objectMapper.readTree(metadataText);
            JsonNode compaction = metadata == null ? null : metadata.get("contextCompaction");
            return compaction == null || compaction.isMissingNode() ? null : compaction;
        } catch (Exception exception) {
            return null;
        }
    }

    private record CompactionMetricSummary(
            long observedCount,
            long criticalCount,
            double avgQualityScore,
            long avgSavedTokens
    ) {
    }
}
