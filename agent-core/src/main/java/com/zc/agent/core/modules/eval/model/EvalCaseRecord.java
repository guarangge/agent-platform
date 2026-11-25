package com.zc.agent.core.modules.eval.model;

import java.time.LocalDateTime;

/**
 * Eval 用例记录。
 */
public record EvalCaseRecord(
        Long caseId,
        Long suiteId,
        String caseCode,
        String caseName,
        String userInput,
        String expectedReplyKeywords,
        String forbiddenReplyKeywords,
        String expectedToolCodes,
        String expectedStepTypes,
        String expectedFinalStatus,
        Integer passThreshold,
        Integer orderNo,
        String status,
        String metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
