package com.zc.agent.core.modules.attachment.vo;

import com.zc.agent.core.modules.attachment.model.ConversationAttachmentRecord;
import java.time.LocalDateTime;

/**
 * 会话附件响应。
 * @param attachmentId 附件ID
 * @param attachmentCode 附件编码
 * @param conversationId 会话ID
 * @param fileName 原始文件名
 * @param mimeType 媒体类型
 * @param sizeBytes 文件大小
 * @param sha256 内容摘要
 * @param status 生命周期状态
 * @param createdAt 创建时间
 */
public record AttachmentResponse(
        Long attachmentId, String attachmentCode, Long conversationId, String fileName,
        String mimeType, Long sizeBytes, String sha256, String status, LocalDateTime createdAt
) {
    /** 将持久化记录转换为不暴露 storageKey 的响应。 */
    public static AttachmentResponse from(ConversationAttachmentRecord record) {
        return new AttachmentResponse(record.attachmentId(), record.attachmentCode(), record.conversationId(),
                record.originalFilename(), record.mimeType(), record.sizeBytes(), record.sha256(),
                record.status(), record.createdAt());
    }
}
