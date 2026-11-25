package com.zc.agent.core.modules.eval.vo;

import java.util.List;

/**
 * Eval Suite 版本差异响应。
 */
public record EvalSuiteDiffResponse(
        Long suiteId,
        Integer fromVersion,
        Integer toVersion,
        DiffSummary summary,
        List<DiffItem> changedItems
) {
    public record DiffSummary(
            Integer addedCount,
            Integer removedCount,
            Integer changedCount
    ) {
    }

    public record DiffItem(
            String changeType,
            String itemKey,
            String beforeText,
            String afterText
    ) {
    }
}
