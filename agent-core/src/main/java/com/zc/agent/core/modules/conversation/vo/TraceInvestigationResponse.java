package com.zc.agent.core.modules.conversation.vo;

import java.util.List;
import java.util.Map;

/**
 * Trace 关联资源排查结果。
 *
 * @param trace            Trace 基础记录
 * @param promptSnapshot   可回放 Prompt 快照
 * @param toolInputObservation Tool 输入治理观测
 * @param promptReferences Prompt 关联信息
 * @param toolReferences   Tool 关联信息
 * @param replayable       是否具备回放所需的完整 Prompt
 * @param replayBlockReason 不可回放原因
 */
public record TraceInvestigationResponse(
        TaskTraceAnalyticsResponse trace,
        Map<String, Object> promptSnapshot,
        Map<String, Object> toolInputObservation,
        List<Map<String, Object>> promptReferences,
        List<Map<String, Object>> toolReferences,
        boolean replayable,
        String replayBlockReason
) {
}
