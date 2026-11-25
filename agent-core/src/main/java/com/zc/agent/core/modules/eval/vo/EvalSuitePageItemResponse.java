package com.zc.agent.core.modules.eval.vo;

import java.time.LocalDateTime;

/**
 * Eval Suite 分页项响应。
 */
public record EvalSuitePageItemResponse(
        Long suiteId,
        Integer currentVersion,
        String suiteCode,
        String suiteName,
        Long agentId,
        String agentName,
        String description,
        String status,
        Integer caseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
