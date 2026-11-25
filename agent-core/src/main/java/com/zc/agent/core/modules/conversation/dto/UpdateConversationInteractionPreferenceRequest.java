package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 更新会话交互偏好请求。
 *
 * @param responseStreamingEnabled 是否启用流式响应
 */
public record UpdateConversationInteractionPreferenceRequest(
        @NotNull(message = "流式响应开关不能为空")
        Boolean responseStreamingEnabled
) {
}
