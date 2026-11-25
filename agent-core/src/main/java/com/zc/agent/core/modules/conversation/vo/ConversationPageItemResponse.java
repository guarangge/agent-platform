package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 会话分页项响应。
 *
 * @param conversationId     会话 ID
 * @param conversationCode   会话编码
 * @param agentId            Agent ID
 * @param agentCode          Agent 编码
 * @param agentName          Agent 名称
 * @param title              会话标题
 * @param status             会话状态
 * @param lastMessageAt      最近消息时间
 * @param lastMessagePreview 最近消息摘要
 * @param createdAt          创建时间
 */
public record ConversationPageItemResponse(
        Long conversationId,
        String conversationCode,
        Long agentId,
        String agentCode,
        String agentName,
        String title,
        String status,
        LocalDateTime lastMessageAt,
        String lastMessagePreview,
        LocalDateTime createdAt
) {
}
