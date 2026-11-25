package com.zc.agent.core.modules.memory.model;

/**
 * Memory 更新草稿。
 *
 * @param userMessage                    本轮用户消息
 * @param assistantContent               本轮助手回复
 * @param conversationMemorySummary      会话级记忆摘要
 * @param conversationPreferenceSummary  会话级偏好摘要
 * @param lastUserMessage                最近用户问题
 * @param lastAssistantSummary           最近响应摘要
 * @param userPreferenceSummary          用户级偏好记忆
 * @param userAttentionSummary           用户级关注点记忆
 * @param userFactSummary                用户级长期事实
 * @param userGoalSummary                用户级长期目标
 * @param taskMemorySummary              执行级记忆摘要
 * @param taskKeyFindings                执行级关键发现
 * @param taskFinalResponseSummary       执行级最终响应摘要
 */
public record MemoryUpdateDraft(
        String userMessage,
        String assistantContent,
        String conversationMemorySummary,
        String conversationPreferenceSummary,
        String lastUserMessage,
        String lastAssistantSummary,
        String userPreferenceSummary,
        String userAttentionSummary,
        String userFactSummary,
        String userGoalSummary,
        String taskMemorySummary,
        String taskKeyFindings,
        String taskFinalResponseSummary
) {
}
