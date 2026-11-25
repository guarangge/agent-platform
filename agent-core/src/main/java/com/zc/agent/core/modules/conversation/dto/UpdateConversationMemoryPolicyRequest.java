package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新会话记忆策略请求。
 *
 * @param userMemoryEnabled         是否启用用户历史记忆
 * @param userMemoryMode            用户历史记忆注入模式
 * @param userMemoryWritebackEnabled 是否允许写回用户长期记忆
 * @param preferSummaryOverRawHistory 是否优先摘要压缩历史
 * @param maxRecentMessages 最近消息窗口大小
 * @param maxRecentUserMessages 最近用户消息窗口大小
 * @param preferStateFirst 是否优先注入会话状态
 * @param historySummaryThresholdMessages 历史摘要触发阈值
 * @param minRetentionRatio 最低保留比例
 * @param qualityWarningThreshold 压缩质量告警阈值
 */
public record UpdateConversationMemoryPolicyRequest(
        @NotNull(message = "用户历史记忆开关不能为空")
        Boolean userMemoryEnabled,
        @NotNull(message = "用户历史记忆模式不能为空")
        @Size(max = 32, message = "用户历史记忆模式长度不能超过32个字符")
        String userMemoryMode,
        @NotNull(message = "用户历史记忆写回开关不能为空")
        Boolean userMemoryWritebackEnabled,
        Boolean preferSummaryOverRawHistory,
        Integer maxRecentMessages,
        Integer maxRecentUserMessages,
        Boolean preferStateFirst,
        Integer historySummaryThresholdMessages,
        Double minRetentionRatio,
        Integer qualityWarningThreshold
) {
}
