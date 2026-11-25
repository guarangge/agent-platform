package com.zc.agent.core.modules.eval.vo;

import java.time.LocalDateTime;

/**
 * Eval Suite 版本快照响应。
 */
public record EvalSuiteVersionResponse(
        Long versionId,
        Long suiteId,
        Integer versionNo,
        String suiteCode,
        String suiteName,
        String agentName,
        String status,
        Integer caseCount,
        String metadataJson,
        LocalDateTime createdAt,
        Long createdBy
) {
}
