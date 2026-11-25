package com.zc.agent.core.modules.eval.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eval Run 分页项响应。
 */
public record EvalRunPageItemResponse(
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
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
