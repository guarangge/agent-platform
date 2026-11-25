package com.zc.agent.core.modules.attachment.model;

/**
 * 受权读取后的附件正文。
 * @param filename 文件名
 * @param mimeType 媒体类型
 * @param bytes 文件正文
 */
public record AttachmentContent(String filename, String mimeType, byte[] bytes) {
}

