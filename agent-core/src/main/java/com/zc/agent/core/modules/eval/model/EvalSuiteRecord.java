package com.zc.agent.core.modules.eval.model;

import java.time.LocalDateTime;

/**
 * Eval Suite 记录。
 */
public record EvalSuiteRecord(
        Long suiteId,
        Integer currentVersion,
        String suiteCode,
        String suiteName,
        Long agentId,
        String agentName,
        String description,
        String status,
        String metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
