package com.zc.agent.core.modules.eval.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Eval Run 用例结果响应。
 */
public record EvalRunCaseResponse(
        Long runCaseId,
        Long caseId,
        String caseCode,
        String caseName,
        Long conversationId,
        Long taskId,
        String traceId,
        String runStatus,
        Boolean passed,
        BigDecimal score,
        BigDecimal ruleScore,
        BigDecimal judgeScore,
        Boolean judgePassed,
        String judgeRationale,
        String actualReply,
        List<String> actualToolCodes,
        List<String> actualStepTypes,
        String actualFinalStatus,
        BigDecimal baselineScore,
        Boolean baselinePassed,
        BigDecimal scoreDelta,
        List<String> failureReasons,
        Map<String, Object> metadata,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
