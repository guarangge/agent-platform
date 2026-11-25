package com.zc.agent.core.modules.attachment.model;

import java.time.LocalDateTime;

/**
 * 会话附件持久化记录。
 *
 * @param attachmentId 附件ID
 * @param attachmentCode 附件编码
 * @param conversationId 会话ID
 * @param messageId 关联消息ID
 * @param runCode 关联Run编码
 * @param direction 输入输出方向
 * @param originalFilename 原始文件名
 * @param mimeType 文件签名识别的媒体类型
 * @param sizeBytes 文件大小
 * @param sha256 内容摘要
 * @param storageProvider 存储实现编码
 * @param storageKey 存储内部键
 * @param status 生命周期状态
 * @param createdAt 创建时间
 */
public record ConversationAttachmentRecord(
        Long attachmentId, String attachmentCode, Long conversationId, Long messageId, String runCode,
        String direction, String originalFilename, String mimeType, Long sizeBytes, String sha256,
        String storageProvider, String storageKey, String status, LocalDateTime createdAt
) {
}

