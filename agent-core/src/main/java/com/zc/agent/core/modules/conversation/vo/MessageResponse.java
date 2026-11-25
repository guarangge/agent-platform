package com.zc.agent.core.modules.conversation.vo;

import com.zc.agent.core.modules.attachment.vo.AttachmentResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息响应。
 *
 * @param messageId      消息 ID
 * @param conversationId 会话 ID
 * @param messageType    消息类型
 * @param sequenceNo     顺序号
 * @param content        内容
 * @param responseStatus 响应状态
 * @param errorMessage   错误信息
 * @param createdAt      创建时间
 * @param attachments    消息附件
 */
public record MessageResponse(
        Long messageId,
        Long conversationId,
        String messageType,
        Integer sequenceNo,
        String content,
        String responseStatus,
        String errorMessage,
        LocalDateTime createdAt,
        List<AttachmentResponse> attachments
) {
    /** 保留不含附件的旧构造契约。 */
    public MessageResponse(Long messageId, Long conversationId, String messageType, Integer sequenceNo,
                           String content, String responseStatus, String errorMessage, LocalDateTime createdAt) {
        this(messageId, conversationId, messageType, sequenceNo, content, responseStatus, errorMessage,
                createdAt, List.of());
    }
}
