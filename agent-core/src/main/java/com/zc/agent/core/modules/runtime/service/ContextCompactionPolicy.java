package com.zc.agent.core.modules.runtime.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 不可变上下文压缩策略，由独立解析器按会话优先、Agent 兜底生成。 */
public record ContextCompactionPolicy(boolean enabled, boolean preferSummaryOverRawHistory,
        int maxRecentMessages, int maxRecentUserMessages, boolean preferStateFirst,
        int historySummaryThresholdMessages, double minRetentionRatio, int qualityWarningThreshold) {
    public static ContextCompactionPolicy defaults() {
        return new ContextCompactionPolicy(true, true, 6, 4, true, 6, 0.18D, 75);
    }
    public Map<String,Object> toMetadata() {
        LinkedHashMap<String,Object> value = new LinkedHashMap<>();
        value.put("enabled", enabled);value.put("preferSummaryOverRawHistory", preferSummaryOverRawHistory);
        value.put("maxRecentMessages", maxRecentMessages);value.put("maxRecentUserMessages", maxRecentUserMessages);
        value.put("preferStateFirst", preferStateFirst);value.put("historySummaryThresholdMessages", historySummaryThresholdMessages);
        value.put("minRetentionRatio", minRetentionRatio);value.put("qualityWarningThreshold", qualityWarningThreshold);
        return value;
    }
    public ContextCompactionPolicy toRetryPolicy() {
        return new ContextCompactionPolicy(enabled, false, Math.max(3,maxRecentMessages-2),
                Math.max(2,maxRecentUserMessages-1), true, Math.max(3,historySummaryThresholdMessages-2),
                Math.max(minRetentionRatio,0.28D), qualityWarningThreshold);
    }
}
