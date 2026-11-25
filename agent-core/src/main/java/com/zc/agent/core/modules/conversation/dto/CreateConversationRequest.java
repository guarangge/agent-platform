package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。
 *
 * @param agentId                  Agent ID
 * @param title                    会话标题
 * @param userMemoryEnabled        是否启用用户历史记忆
 * @param userMemoryMode           用户历史记忆注入模式
 * @param userMemoryWritebackEnabled 是否允许写回用户长期记忆
 * @param preferSummaryOverRawHistory 是否优先摘要压缩历史
 * @param maxRecentMessages 最近消息窗口大小
 * @param maxRecentUserMessages 最近用户消息窗口大小
 * @param preferStateFirst 是否优先注入会话状态
 * @param historySummaryThresholdMessages 历史摘要触发阈值
 * @param minRetentionRatio 最低保留比例
 * @param qualityWarningThreshold 压缩质量告警阈值
 */
public record CreateConversationRequest(
        @NotNull(message = "Agent ID 不能为空")
        @Positive(message = "Agent ID 必须为正整数")
        Long agentId,
        @Size(max = 128, message = "会话名称长度不能超过128个字符")
        String title,
        Boolean userMemoryEnabled,
        @Size(max = 32, message = "用户历史记忆模式长度不能超过32个字符")
        String userMemoryMode,
        Boolean userMemoryWritebackEnabled,
        Boolean preferSummaryOverRawHistory,
        @Positive(message = "最近消息窗口必须为正整数")
        Integer maxRecentMessages,
        @Positive(message = "最近用户消息窗口必须为正整数")
        Integer maxRecentUserMessages,
        Boolean preferStateFirst,
        @Positive(message = "历史摘要触发阈值必须为正整数")
        Integer historySummaryThresholdMessages,
        Double minRetentionRatio,
        Integer qualityWarningThreshold
) {
}
