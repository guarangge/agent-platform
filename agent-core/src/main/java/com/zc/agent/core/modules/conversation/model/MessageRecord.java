package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 消息记录。
 *
 * @param messageId       消息 ID
 * @param conversationId  会话 ID
 * @param messageType     消息类型
 * @param sequenceNo      顺序号
 * @param content         内容
 * @param responseStatus  响应状态
 * @param errorMessage    错误信息
 * @param createdAt       创建时间
 */
public record MessageRecord(
        Long messageId,
        Long conversationId,
        String messageType,
        Integer sequenceNo,
        String content,
        String responseStatus,
        String errorMessage,
        LocalDateTime createdAt
) {
}
