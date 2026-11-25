package com.zc.agent.core.modules.eval.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Eval Run 详情响应。
 */
public record EvalRunDetailResponse(
        Long runId,
        Long suiteId,
        Integer suiteVersion,
        String suiteCode,
        String suiteName,
        Long agentId,
        String agentName,
        String runCode,
        String runStatus,
        Integer totalCases,
        Integer passedCases,
        Integer failedCases,
        BigDecimal averageScore,
        String triggerType,
        Map<String, Object> metadata,
        BaselineComparisonResponse baselineComparison,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        List<EvalRunCaseResponse> runCases
) {
    public record BaselineComparisonResponse(
            Long baselineRunId,
            String baselineRunCode,
            BigDecimal baselineAverageScore,
            BigDecimal scoreDelta,
            Integer passDelta,
            Integer improvedCaseCount,
            Integer regressedCaseCount,
            Integer unchangedCaseCount
    ) {
    }
}
