package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 会话详情响应。
 *
 * @param conversationId     会话 ID
 * @param conversationCode   会话编码
 * @param agentId            Agent ID
 * @param agentCode          Agent 编码
 * @param agentName          Agent 名称
 * @param defaultModel       默认模型
 * @param defaultPromptId    默认 Prompt ID
 * @param defaultPromptName  默认 Prompt 名称
 * @param decisionPromptId   决策 Prompt ID
 * @param decisionPromptName 决策 Prompt 名称
 * @param plannerPromptId    规划 Prompt ID
 * @param plannerPromptName  规划 Prompt 名称
 * @param title              会话标题
 * @param status             会话状态
 * @param responseStreamingEnabled 当前会话是否启用流式响应
 * @param lastMessageAt      最近消息时间
 * @param lastMessagePreview 最近消息摘要
 * @param memorySummary      会话记忆摘要
 * @param preferenceSummary  偏好摘要
 * @param lastUserMessage    最近用户消息摘要
 * @param lastAssistantSummary 最近助手消息摘要
 * @param memoryUpdatedAt    记忆更新时间
 * @param userPreferenceSummary 用户级偏好摘要
 * @param userAttentionSummary 用户级关注点摘要
 * @param userFactSummary 用户级长期事实摘要
 * @param userGoalSummary 用户级长期目标摘要
 * @param userMemoryUpdatedAt 用户记忆更新时间
 * @param userMemoryEnabled 当前会话是否启用用户历史记忆
 * @param userMemoryMode 当前会话用户历史记忆注入模式
 * @param userMemoryWritebackEnabled 当前会话是否允许写回用户长期记忆
 * @param agentDefaultUserMemoryEnabled Agent 默认是否启用用户历史记忆
 * @param agentDefaultUserMemoryMode Agent 默认用户历史记忆模式
 * @param agentDefaultUserMemoryWritebackEnabled Agent 默认是否允许写回用户长期记忆
 * @param preferSummaryOverRawHistory 当前会话是否优先摘要压缩历史
 * @param maxRecentMessages 当前会话最近消息窗口大小
 * @param maxRecentUserMessages 当前会话最近用户消息窗口大小
 * @param preferStateFirst 当前会话是否优先注入状态
 * @param historySummaryThresholdMessages 当前会话摘要触发阈值
 * @param minRetentionRatio 当前会话最低保留比例
 * @param qualityWarningThreshold 当前会话质量告警阈值
 * @param agentDefaultPreferSummaryOverRawHistory Agent 默认是否优先摘要压缩历史
 * @param agentDefaultMaxRecentMessages Agent 默认最近消息窗口大小
 * @param agentDefaultMaxRecentUserMessages Agent 默认最近用户消息窗口大小
 * @param agentDefaultPreferStateFirst Agent 默认是否优先注入状态
 * @param agentDefaultHistorySummaryThresholdMessages Agent 默认摘要触发阈值
 * @param agentDefaultMinRetentionRatio Agent 默认最低保留比例
 * @param agentDefaultQualityWarningThreshold Agent 默认质量告警阈值
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 */
public record ConversationDetailResponse(
        Long conversationId,
        String conversationCode,
        Long agentId,
        String agentCode,
        String agentName,
        String defaultModel,
        Long defaultPromptId,
        String defaultPromptName,
        Long decisionPromptId,
        String decisionPromptName,
        Long plannerPromptId,
        String plannerPromptName,
        String title,
        String status,
        Boolean responseStreamingEnabled,
        LocalDateTime lastMessageAt,
        String lastMessagePreview,
        String memorySummary,
        String preferenceSummary,
        String lastUserMessage,
        String lastAssistantSummary,
        LocalDateTime memoryUpdatedAt,
        String userPreferenceSummary,
        String userAttentionSummary,
        String userFactSummary,
        String userGoalSummary,
        LocalDateTime userMemoryUpdatedAt,
        Boolean userMemoryEnabled,
        String userMemoryMode,
        Boolean userMemoryWritebackEnabled,
        Boolean agentDefaultUserMemoryEnabled,
        String agentDefaultUserMemoryMode,
        Boolean agentDefaultUserMemoryWritebackEnabled,
        Boolean preferSummaryOverRawHistory,
        Integer maxRecentMessages,
        Integer maxRecentUserMessages,
        Boolean preferStateFirst,
        Integer historySummaryThresholdMessages,
        Double minRetentionRatio,
        Integer qualityWarningThreshold,
        Boolean agentDefaultPreferSummaryOverRawHistory,
        Integer agentDefaultMaxRecentMessages,
        Integer agentDefaultMaxRecentUserMessages,
        Boolean agentDefaultPreferStateFirst,
        Integer agentDefaultHistorySummaryThresholdMessages,
        Double agentDefaultMinRetentionRatio,
        Integer agentDefaultQualityWarningThreshold,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
