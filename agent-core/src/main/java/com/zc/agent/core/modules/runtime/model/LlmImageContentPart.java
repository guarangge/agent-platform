package com.zc.agent.core.modules.runtime.model;

/**
 * 模型调用期图片内容，不进入持久化快照。
 * @param attachmentId 附件ID
 * @param mimeType 媒体类型
 * @param content 文件正文
 */
public record LlmImageContentPart(Long attachmentId, String mimeType, byte[] content) implements LlmContentPart {
}
