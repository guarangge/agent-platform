package com.zc.agent.core.modules.eval.model;

import java.time.LocalDateTime;

/**
 * Eval Suite 版本快照记录。
 */
public record EvalSuiteVersionRecord(
        Long versionId,
        Long suiteId,
        Integer versionNo,
        String suiteCode,
        String suiteName,
        Long agentId,
        String agentName,
        String description,
        String status,
        String metadata,
        String casesJson,
        LocalDateTime createdAt,
        Long createdBy
) {
}
