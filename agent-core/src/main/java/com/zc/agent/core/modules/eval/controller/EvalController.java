package com.zc.agent.core.modules.eval.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.eval.dto.CreateEvalSuiteRequest;
import com.zc.agent.core.modules.eval.dto.EvalRunPageQueryRequest;
import com.zc.agent.core.modules.eval.dto.EvalSuitePageQueryRequest;
import com.zc.agent.core.modules.eval.dto.ExecuteEvalSuiteRequest;
import com.zc.agent.core.modules.eval.dto.UpdateEvalSuiteRequest;
import com.zc.agent.core.modules.eval.service.EvalService;
import com.zc.agent.core.modules.eval.service.EvalReleaseGateService;
import com.zc.agent.core.modules.eval.vo.AgentReleaseGateResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunDetailResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunPageItemResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunCompareResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteDetailResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteDiffResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuitePageItemResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteVersionResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Eval Harness 管理接口。
 */
@Validated
@RestController
@RequestMapping("/api/core/evals")
public class EvalController {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EvalService evalService;
    private final ObjectMapper objectMapper;
    private final EvalReleaseGateService evalReleaseGateService;
    private final String releaseGateServiceToken;

    public EvalController(EvalService evalService,
                          ObjectMapper objectMapper,
                          EvalReleaseGateService evalReleaseGateService,
                          @org.springframework.beans.factory.annotation.Value("${core.registry.service-token}") String releaseGateServiceToken) {
        this.evalService = evalService;
        this.objectMapper = objectMapper;
        this.evalReleaseGateService = evalReleaseGateService;
        this.releaseGateServiceToken = releaseGateServiceToken;
    }

    /** 仅供 Registry 在 Agent 启用前调用的 Eval 发布门禁。 */
    @GetMapping("/release-gate/agents/{agentId}")
    public ApiResponse<AgentReleaseGateResponse> evaluateReleaseGate(
            @PathVariable Long agentId,
            @RequestHeader("X-Service-Token") String serviceToken,
            @RequestParam(defaultValue = "80") BigDecimal minimumAverageScore,
            @RequestParam(defaultValue = "90") BigDecimal minimumPassRate,
            @RequestParam(defaultValue = "1") Integer minimumCases,
            @RequestParam(defaultValue = "168") Integer maximumAgeHours) {
        if (!MessageDigest.isEqual(releaseGateServiceToken.getBytes(StandardCharsets.UTF_8),
                serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new com.zc.agent.core.common.exception.BusinessException("发布门禁服务令牌不合法");
        }
        return ApiResponse.success(evalReleaseGateService.evaluate(agentId, minimumAverageScore,
                minimumPassRate, minimumCases, maximumAgeHours));
    }

    @GetMapping("/suites")
    public ApiResponse<PageResponse<EvalSuitePageItemResponse>> pageSuites(@Valid EvalSuitePageQueryRequest request) {
        return ApiResponse.success(evalService.pageSuites(request));
    }

    @GetMapping("/suites/{suiteId}")
    public ApiResponse<EvalSuiteDetailResponse> getSuiteDetail(@PathVariable Long suiteId) {
        return ApiResponse.success(evalService.getSuiteDetail(suiteId));
    }

    @GetMapping("/suites/{suiteId}/versions")
    public ApiResponse<List<EvalSuiteVersionResponse>> listSuiteVersions(@PathVariable Long suiteId) {
        return ApiResponse.success(evalService.listSuiteVersions(suiteId));
    }

    @GetMapping("/suites/{suiteId}/versions/diff")
    public ApiResponse<EvalSuiteDiffResponse> getSuiteDiff(@PathVariable Long suiteId,
                                                           Integer fromVersion,
                                                           Integer toVersion) {
        return ApiResponse.success(evalService.getSuiteDiff(suiteId, fromVersion, toVersion));
    }

    @GetMapping("/suites/{suiteId}/versions/diff/export")
    public ResponseEntity<byte[]> exportSuiteDiff(@PathVariable Long suiteId,
                                                  Integer fromVersion,
                                                  Integer toVersion) {
        EvalSuiteDiffResponse response = evalService.getSuiteDiff(suiteId, fromVersion, toVersion);
        String fileName = "eval-suite-diff-" + suiteId + "-v" + fromVersion + "-v" + toVersion + "-"
                + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".csv";
        return csvResponse(fileName, buildSuiteDiffCsv(response));
    }

    @PostMapping("/suites")
    public ApiResponse<EvalSuiteDetailResponse> createSuite(@Valid @RequestBody CreateEvalSuiteRequest request,
                                                            Authentication authentication) {
        return ApiResponse.success(evalService.createSuite(request, (SecurityUser) authentication.getPrincipal()));
    }

    @PutMapping("/suites/{suiteId}")
    public ApiResponse<EvalSuiteDetailResponse> updateSuite(@PathVariable Long suiteId,
                                                            @Valid @RequestBody UpdateEvalSuiteRequest request,
                                                            Authentication authentication) {
        return ApiResponse.success(evalService.updateSuite(suiteId, request, (SecurityUser) authentication.getPrincipal()));
    }

    @PutMapping("/suites/{suiteId}/baseline/{runId}")
    public ApiResponse<EvalSuiteDetailResponse> markBaselineRun(@PathVariable Long suiteId,
                                                                @PathVariable Long runId,
                                                                Authentication authentication) {
        return ApiResponse.success(evalService.markBaselineRun(suiteId, runId, (SecurityUser) authentication.getPrincipal()));
    }

    @PostMapping("/suites/{suiteId}/runs")
    public ApiResponse<EvalRunDetailResponse> executeSuite(@PathVariable Long suiteId,
                                                           @RequestBody(required = false) ExecuteEvalSuiteRequest request,
                                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                           Authentication authentication) {
        return ApiResponse.success(evalService.executeSuite(suiteId, request, authorization, (SecurityUser) authentication.getPrincipal()));
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<EvalRunPageItemResponse>> pageRuns(@Valid EvalRunPageQueryRequest request) {
        return ApiResponse.success(evalService.pageRuns(request));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<EvalRunDetailResponse> getRunDetail(@PathVariable Long runId) {
        return ApiResponse.success(evalService.getRunDetail(runId));
    }

    @GetMapping("/runs/{runId}/export")
    public ResponseEntity<byte[]> exportRunDetail(@PathVariable Long runId) {
        EvalRunDetailResponse response = evalService.getRunDetail(runId);
        String fileName = "eval-run-" + response.runCode() + "-"
                + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".csv";
        return csvResponse(fileName, buildRunDetailCsv(response));
    }

    @GetMapping("/runs/{runId}/report.json")
    public ResponseEntity<byte[]> exportRunJsonReport(@PathVariable Long runId) {
        EvalRunDetailResponse response = evalService.getRunDetail(runId);
        String fileName = "eval-run-report-" + response.runCode() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(toJsonBytes(response));
    }

    @GetMapping("/runs/{runId}/report.md")
    public ResponseEntity<byte[]> exportRunMarkdownReport(@PathVariable Long runId) {
        EvalRunDetailResponse response = evalService.getRunDetail(runId);
        String fileName = "eval-run-report-" + response.runCode() + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(buildRunMarkdownReport(response).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/runs/compare")
    public ApiResponse<EvalRunCompareResponse> compareRuns(Long fromRunId,
                                                           Long toRunId) {
        return ApiResponse.success(evalService.compareRuns(fromRunId, toRunId));
    }

    @GetMapping("/runs/compare/export")
    public ResponseEntity<byte[]> exportRunCompare(Long fromRunId,
                                                   Long toRunId) {
        EvalRunCompareResponse response = evalService.compareRuns(fromRunId, toRunId);
        String fileName = "eval-run-compare-" + response.fromRunCode() + "-" + response.toRunCode() + "-"
                + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".csv";
        return csvResponse(fileName, buildRunCompareCsv(response));
    }

    private ResponseEntity<byte[]> csvResponse(String fileName, String csvContent) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    private String buildSuiteDiffCsv(EvalSuiteDiffResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("suiteId,fromVersion,toVersion,addedCount,removedCount,changedCount\n");
        builder.append(csv(response.suiteId()))
                .append(csv(response.fromVersion()))
                .append(csv(response.toVersion()))
                .append(csv(response.summary().addedCount()))
                .append(csv(response.summary().removedCount()))
                .append(csv(response.summary().changedCount()))
                .append('\n');
        builder.append('\n');
        builder.append("changeType,itemKey,beforeText,afterText\n");
        for (EvalSuiteDiffResponse.DiffItem item : response.changedItems()) {
            builder.append(csv(item.changeType()))
                    .append(csv(item.itemKey()))
                    .append(csv(item.beforeText()))
                    .append(csv(item.afterText()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String buildRunCompareCsv(EvalRunCompareResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("suiteId,fromRunId,fromRunCode,fromSuiteVersion,toRunId,toRunCode,toSuiteVersion,improvedCount,regressedCount,unchangedCount,addedCount,removedCount,averageScoreDelta,passDelta\n");
        builder.append(csv(response.suiteId()))
                .append(csv(response.fromRunId()))
                .append(csv(response.fromRunCode()))
                .append(csv(response.fromSuiteVersion()))
                .append(csv(response.toRunId()))
                .append(csv(response.toRunCode()))
                .append(csv(response.toSuiteVersion()))
                .append(csv(response.summary().improvedCount()))
                .append(csv(response.summary().regressedCount()))
                .append(csv(response.summary().unchangedCount()))
                .append(csv(response.summary().addedCount()))
                .append(csv(response.summary().removedCount()))
                .append(csv(response.summary().averageScoreDelta()))
                .append(csv(response.summary().passDelta()))
                .append('\n');
        builder.append('\n');
        builder.append("caseCode,caseName,changeType,fromScore,toScore,scoreDelta,fromPassed,toPassed,fromStatus,toStatus,fromReply,toReply\n");
        for (EvalRunCompareResponse.CompareItem item : response.comparedCases()) {
            builder.append(csv(item.caseCode()))
                    .append(csv(item.caseName()))
                    .append(csv(item.changeType()))
                    .append(csv(item.fromScore()))
                    .append(csv(item.toScore()))
                    .append(csv(item.scoreDelta()))
                    .append(csv(item.fromPassed()))
                    .append(csv(item.toPassed()))
                    .append(csv(item.fromStatus()))
                    .append(csv(item.toStatus()))
                    .append(csv(item.fromReply()))
                    .append(csv(item.toReply()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String buildRunDetailCsv(EvalRunDetailResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("runId,runCode,suiteId,suiteCode,suiteName,suiteVersion,agentId,agentName,runStatus,totalCases,passedCases,failedCases,averageScore,triggerType,baselineRunId,baselineRunCode,baselineAverageScore,baselineScoreDelta,baselinePassDelta,startedAt,finishedAt,createdAt\n");
        builder.append(csv(response.runId()))
                .append(csv(response.runCode()))
                .append(csv(response.suiteId()))
                .append(csv(response.suiteCode()))
                .append(csv(response.suiteName()))
                .append(csv(response.suiteVersion()))
                .append(csv(response.agentId()))
                .append(csv(response.agentName()))
                .append(csv(response.runStatus()))
                .append(csv(response.totalCases()))
                .append(csv(response.passedCases()))
                .append(csv(response.failedCases()))
                .append(csv(response.averageScore()))
                .append(csv(response.triggerType()))
                .append(csv(response.baselineComparison() == null ? null : response.baselineComparison().baselineRunId()))
                .append(csv(response.baselineComparison() == null ? null : response.baselineComparison().baselineRunCode()))
                .append(csv(response.baselineComparison() == null ? null : response.baselineComparison().baselineAverageScore()))
                .append(csv(response.baselineComparison() == null ? null : response.baselineComparison().scoreDelta()))
                .append(csv(response.baselineComparison() == null ? null : response.baselineComparison().passDelta()))
                .append(csv(response.startedAt()))
                .append(csv(response.finishedAt()))
                .append(csv(response.createdAt()))
                .append('\n');
        builder.append('\n');
        builder.append("runCaseId,caseId,caseCode,caseName,conversationId,taskId,traceId,runStatus,passed,score,ruleScore,judgeScore,judgePassed,judgeRationale,actualReply,actualToolCodes,actualStepTypes,actualFinalStatus,baselineScore,baselinePassed,scoreDelta,failureReasons,startedAt,finishedAt,createdAt\n");
        for (var item : response.runCases()) {
            builder.append(csv(item.runCaseId()))
                    .append(csv(item.caseId()))
                    .append(csv(item.caseCode()))
                    .append(csv(item.caseName()))
                    .append(csv(item.conversationId()))
                    .append(csv(item.taskId()))
                    .append(csv(item.traceId()))
                    .append(csv(item.runStatus()))
                    .append(csv(item.passed()))
                    .append(csv(item.score()))
                    .append(csv(item.ruleScore()))
                    .append(csv(item.judgeScore()))
                    .append(csv(item.judgePassed()))
                    .append(csv(item.judgeRationale()))
                    .append(csv(item.actualReply()))
                    .append(csv(String.join(" | ", item.actualToolCodes())))
                    .append(csv(String.join(" | ", item.actualStepTypes())))
                    .append(csv(item.actualFinalStatus()))
                    .append(csv(item.baselineScore()))
                    .append(csv(item.baselinePassed()))
                    .append(csv(item.scoreDelta()))
                    .append(csv(String.join(" | ", item.failureReasons())))
                    .append(csv(item.startedAt()))
                    .append(csv(item.finishedAt()))
                    .append(csv(item.createdAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\",";
    }

    private byte[] toJsonBytes(EvalRunDetailResponse response) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导出 Eval JSON 报告失败", exception);
        }
    }

    private String buildRunMarkdownReport(EvalRunDetailResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Eval Run Report\n\n");
        builder.append("- Run Code: ").append(response.runCode()).append('\n');
        builder.append("- Suite: ").append(response.suiteName()).append(" (`").append(response.suiteCode()).append("`)\n");
        builder.append("- Suite Version: v").append(response.suiteVersion()).append('\n');
        builder.append("- Agent: ").append(response.agentName()).append('\n');
        builder.append("- Status: ").append(response.runStatus()).append('\n');
        builder.append("- Average Score: ").append(response.averageScore()).append('\n');
        builder.append("- Passed Cases: ").append(response.passedCases()).append('/').append(response.totalCases()).append('\n');
        if (response.baselineComparison() != null) {
            builder.append("- Baseline: ").append(response.baselineComparison().baselineRunCode())
                    .append(" / Score Delta ").append(response.baselineComparison().scoreDelta())
                    .append(" / Pass Delta ").append(response.baselineComparison().passDelta()).append('\n');
        }
        builder.append("\n## Cases\n\n");
        builder.append("| Case Code | Case Name | Status | Score | Rule Score | Judge Score | Delta | Tools | Step Types |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        response.runCases().forEach(item -> builder.append("| ")
                .append(safe(item.caseCode())).append(" | ")
                .append(safe(item.caseName())).append(" | ")
                .append(safe(item.runStatus())).append(" | ")
                .append(safe(item.score())).append(" | ")
                .append(safe(item.ruleScore())).append(" | ")
                .append(safe(item.judgeScore())).append(" | ")
                .append(safe(item.scoreDelta())).append(" | ")
                .append(String.join(", ", item.actualToolCodes())).append(" | ")
                .append(String.join(", ", item.actualStepTypes())).append(" |\n"));
        return builder.toString();
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
