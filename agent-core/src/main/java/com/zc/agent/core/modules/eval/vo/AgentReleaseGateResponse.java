package com.zc.agent.core.modules.eval.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Agent 发布门禁判定结果。 */
public record AgentReleaseGateResponse(
        Long agentId,
        boolean passed,
        List<String> reasons,
        Long evalRunId,
        String evalRunCode,
        Integer totalCases,
        BigDecimal averageScore,
        BigDecimal passRate,
        LocalDateTime finishedAt
) {
}
