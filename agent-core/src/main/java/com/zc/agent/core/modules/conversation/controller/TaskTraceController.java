package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.TaskTraceQueryRequest;
import com.zc.agent.core.modules.conversation.dto.TraceReplayRequest;
import com.zc.agent.core.modules.conversation.service.TaskTraceAnalyticsService;
import com.zc.agent.core.modules.conversation.service.TraceInvestigationService;
import com.zc.agent.core.modules.conversation.vo.CompactionMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.CompactionTraceResponse;
import com.zc.agent.core.modules.conversation.vo.TaskTraceAnalyticsResponse;
import com.zc.agent.core.modules.conversation.vo.TaskTraceMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.TraceTopologyResponse;
import com.zc.agent.core.modules.conversation.vo.TraceInvestigationResponse;
import com.zc.agent.core.modules.conversation.vo.TraceReplayResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Trace 运营查询接口。
 */
@RestController
@RequestMapping("/api/core/traces")
public class TaskTraceController {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TaskTraceAnalyticsService taskTraceAnalyticsService;
    private final TraceInvestigationService traceInvestigationService;

    public TaskTraceController(TaskTraceAnalyticsService taskTraceAnalyticsService,
                               TraceInvestigationService traceInvestigationService) {
        this.taskTraceAnalyticsService = taskTraceAnalyticsService;
        this.traceInvestigationService = traceInvestigationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskTraceAnalyticsResponse>> page(@Valid TaskTraceQueryRequest request,
                                                                      Authentication authentication) {
        return ApiResponse.success(taskTraceAnalyticsService.page(request, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/metrics")
    public ApiResponse<TaskTraceMetricsResponse> metrics(@Valid TaskTraceQueryRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(taskTraceAnalyticsService.metrics(request, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid TaskTraceQueryRequest request,
                                         Authentication authentication) {
        List<TaskTraceAnalyticsResponse> records = taskTraceAnalyticsService.export(request, (SecurityUser) authentication.getPrincipal());
        String fileName = "trace-export-" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".csv";
        String csvContent = buildCsv(records);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/topology")
    public ApiResponse<TraceTopologyResponse> topology(Long conversationId,
                                                       Long taskId,
                                                       String traceId,
                                                       Authentication authentication) {
        return ApiResponse.success(taskTraceAnalyticsService.topology(conversationId, taskId, traceId, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/{taskTraceId}/investigation")
    public ApiResponse<TraceInvestigationResponse> investigation(@PathVariable Long taskTraceId,
                                                                 Authentication authentication) {
        return ApiResponse.success(traceInvestigationService.investigate(
                taskTraceId,
                (SecurityUser) authentication.getPrincipal()
        ));
    }

    @PostMapping("/{taskTraceId}/replay")
    public ApiResponse<TraceReplayResponse> replay(@PathVariable Long taskTraceId,
                                                   @Valid @RequestBody TraceReplayRequest request,
                                                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                   Authentication authentication) {
        return ApiResponse.success(traceInvestigationService.replay(
                taskTraceId,
                request,
                authorization,
                (SecurityUser) authentication.getPrincipal()
        ));
    }

    @GetMapping("/compaction")
    public ApiResponse<PageResponse<CompactionTraceResponse>> pageCompactions(@Valid TaskTraceQueryRequest request,
                                                                              Authentication authentication) {
        return ApiResponse.success(taskTraceAnalyticsService.pageCompactions(request, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/compaction/metrics")
    public ApiResponse<CompactionMetricsResponse> compactionMetrics(@Valid TaskTraceQueryRequest request,
                                                                    Authentication authentication) {
        return ApiResponse.success(taskTraceAnalyticsService.compactionMetrics(request, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/compaction/export")
    public ResponseEntity<byte[]> exportCompactions(@Valid TaskTraceQueryRequest request,
                                                    Authentication authentication) {
        List<CompactionTraceResponse> records = taskTraceAnalyticsService.exportCompactions(request, (SecurityUser) authentication.getPrincipal());
        String fileName = "trace-compaction-export-" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".csv";
        String csvContent = buildCompactionCsv(records);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    private String buildCsv(List<TaskTraceAnalyticsResponse> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("taskTraceId,conversationId,conversationCode,taskId,taskCode,agentId,agentName,traceId,taskStepId,stepNo,stepName,eventType,spanName,traceStatus,durationMs,errorMessage,startedAt,finishedAt,createdAt\n");
        for (TaskTraceAnalyticsResponse record : records) {
            builder.append(csv(record.taskTraceId()))
                    .append(csv(record.conversationId()))
                    .append(csv(record.conversationCode()))
                    .append(csv(record.taskId()))
                    .append(csv(record.taskCode()))
                    .append(csv(record.agentId()))
                    .append(csv(record.agentName()))
                    .append(csv(record.traceId()))
                    .append(csv(record.taskStepId()))
                    .append(csv(record.stepNo()))
                    .append(csv(record.stepName()))
                    .append(csv(record.eventType()))
                    .append(csv(record.spanName()))
                    .append(csv(record.traceStatus()))
                    .append(csv(record.durationMs()))
                    .append(csv(record.errorMessage()))
                    .append(csv(record.startedAt()))
                    .append(csv(record.finishedAt()))
                    .append(csv(record.createdAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\",";
    }

    private String buildCompactionCsv(List<CompactionTraceResponse> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("taskTraceId,conversationCode,taskCode,agentName,traceId,eventType,spanName,traceStatus,stage,qualityStatus,qualityScore,savedChars,savedTokens,tokenizerFamily,startedAt,createdAt\n");
        for (CompactionTraceResponse record : records) {
            builder.append(csv(record.taskTraceId()))
                    .append(csv(record.conversationCode()))
                    .append(csv(record.taskCode()))
                    .append(csv(record.agentName()))
                    .append(csv(record.traceId()))
                    .append(csv(record.eventType()))
                    .append(csv(record.spanName()))
                    .append(csv(record.traceStatus()))
                    .append(csv(record.stage()))
                    .append(csv(record.qualityStatus()))
                    .append(csv(record.qualityScore()))
                    .append(csv(record.savedChars()))
                    .append(csv(record.savedTokens()))
                    .append(csv(record.tokenizerFamily()))
                    .append(csv(record.startedAt()))
                    .append(csv(record.createdAt()))
                    .append('\n');
        }
        return builder.toString();
    }
}
