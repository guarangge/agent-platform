package com.zc.agent.core.modules.eval.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eval Run 用例结果记录。
 */
public record EvalRunCaseRecord(
        Long runCaseId,
        Long runId,
        Long caseId,
        String caseCode,
        String caseName,
        Long conversationId,
        Long taskId,
        String traceId,
        String runStatus,
        Boolean passed,
        BigDecimal score,
        String actualReply,
        String actualToolCodes,
        String actualStepTypes,
        String actualFinalStatus,
        String failureReasons,
        String metadata,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
