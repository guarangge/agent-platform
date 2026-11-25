package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新会话标题请求。
 *
 * @param title 会话标题
 */
public record UpdateConversationTitleRequest(
        @NotBlank(message = "会话名称不能为空")
        @Size(max = 128, message = "会话名称长度不能超过128个字符")
        String title
) {
}
