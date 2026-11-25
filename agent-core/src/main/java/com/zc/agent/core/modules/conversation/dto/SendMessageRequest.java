package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发送消息请求。
 *
 * @param content 消息内容
 * @param attachmentIds 已上传的附件ID
 */
public record SendMessageRequest(
        String content,
        @Size(max = 5, message = "单次消息最多关联5个附件")
        List<@Positive(message = "附件ID必须为正数") Long> attachmentIds
) {
    /** 保留纯文本调用方的旧构造契约。 */
    public SendMessageRequest(String content) {
        this(content, List.of());
    }

    /** 文本和附件至少提供一种输入。 */
    @AssertTrue(message = "消息内容和附件不能同时为空")
    public boolean hasInput() {
        return content != null && !content.isBlank() || !normalizedAttachmentIds().isEmpty();
    }

    /** 返回去重后的安全附件ID列表。 */
    public List<Long> normalizedAttachmentIds() {
        if (attachmentIds == null) return List.of();
        return attachmentIds.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    /** 返回运行时使用的文本；只有图片时提供稳定默认意图。 */
    public String normalizedContent() {
        if (content != null && !content.isBlank()) return content.trim();
        return normalizedAttachmentIds().isEmpty() ? "" : "请分析上传的图片";
    }
}
