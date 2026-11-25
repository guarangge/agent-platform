package com.zc.agent.core.modules.eval.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eval Run 记录。
 */
public record EvalRunRecord(
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
        String metadata,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
