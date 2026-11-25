package com.zc.agent.core.modules.eval.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Eval Suite 详情响应。
 */
public record EvalSuiteDetailResponse(
        Long suiteId,
        Integer currentVersion,
        String suiteCode,
        String suiteName,
        Long agentId,
        String agentName,
        String description,
        String status,
        Map<String, Object> metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<EvalCaseResponse> evalCases
) {
}
