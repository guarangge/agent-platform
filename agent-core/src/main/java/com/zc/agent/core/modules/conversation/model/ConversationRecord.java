package com.zc.agent.core.modules.conversation.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 会话主记录。
 *
 * @param conversationId      会话 ID
 * @param conversationCode    会话编码
 * @param userId              用户 ID
 * @param agentId             Agent ID
 * @param agentCode           Agent 编码
 * @param agentName           Agent 名称
 * @param defaultModel        默认模型
 * @param defaultPromptId     默认 Prompt ID
 * @param defaultPromptName   默认 Prompt 名称
 * @param decisionPromptId    决策 Prompt ID
 * @param decisionPromptName  决策 Prompt 名称
 * @param plannerPromptId     规划 Prompt ID
 * @param plannerPromptName   规划 Prompt 名称
 * @param title               会话标题
 * @param status              会话状态
 * @param lastMessageAt       最近消息时间
 * @param lastMessagePreview  最近消息摘要
 * @param metadata            会话元数据
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record ConversationRecord(
        Long conversationId,
        String conversationCode,
        Long userId,
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
        LocalDateTime lastMessageAt,
        String lastMessagePreview,
        JsonNode metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
