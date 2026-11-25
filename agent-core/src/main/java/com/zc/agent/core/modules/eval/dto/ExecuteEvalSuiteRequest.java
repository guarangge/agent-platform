package com.zc.agent.core.modules.eval.dto;

import java.util.List;

/**
 * 执行 Eval Suite 请求。
 */
public record ExecuteEvalSuiteRequest(
        String triggerType,
        Long baselineRunId,
        String scenario,
        List<String> caseTags,
        Boolean enforceCiGate
) {
}
