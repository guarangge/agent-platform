package com.zc.agent.core.modules.eval.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Eval 用例响应。
 */
public record EvalCaseResponse(
        Long caseId,
        String caseCode,
        String caseName,
        String userInput,
        List<String> expectedReplyKeywords,
        List<String> forbiddenReplyKeywords,
        List<String> expectedToolCodes,
        List<String> expectedStepTypes,
        String expectedFinalStatus,
        Integer passThreshold,
        Integer orderNo,
        String status,
        Map<String, Object> metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
