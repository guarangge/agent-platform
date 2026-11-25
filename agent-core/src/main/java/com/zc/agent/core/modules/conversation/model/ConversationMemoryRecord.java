package com.zc.agent.core.modules.conversation.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 会话记忆记录。
 *
 * @param conversationMemoryId 会话记忆 ID
 * @param conversationId       会话 ID
 * @param memorySummary        记忆摘要
 * @param preferenceSummary    偏好摘要
 * @param lastUserMessage      最近用户消息摘要
 * @param lastAssistantSummary 最近助手响应摘要
 * @param metadata            扩展信息
 * @param updatedAt            更新时间
 */
public record ConversationMemoryRecord(
        Long conversationMemoryId,
        Long conversationId,
        String memorySummary,
        String preferenceSummary,
        String lastUserMessage,
        String lastAssistantSummary,
        JsonNode metadata,
        LocalDateTime updatedAt
) {
}
