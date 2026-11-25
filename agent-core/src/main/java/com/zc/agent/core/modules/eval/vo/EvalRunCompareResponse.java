package com.zc.agent.core.modules.eval.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Eval Run 对比响应。
 */
public record EvalRunCompareResponse(
        Long suiteId,
        Integer fromRunId,
        String fromRunCode,
        Integer fromSuiteVersion,
        Integer toRunId,
        String toRunCode,
        Integer toSuiteVersion,
        CompareSummary summary,
        List<CompareItem> comparedCases
) {
    public record CompareSummary(
            Integer improvedCount,
            Integer regressedCount,
            Integer unchangedCount,
            Integer addedCount,
            Integer removedCount,
            BigDecimal averageScoreDelta,
            Integer passDelta
    ) {
    }

    public record CompareItem(
            String caseCode,
            String caseName,
            String changeType,
            BigDecimal fromScore,
            BigDecimal toScore,
            BigDecimal scoreDelta,
            Boolean fromPassed,
            Boolean toPassed,
            String fromStatus,
            String toStatus,
            String fromReply,
            String toReply
    ) {
    }
}
