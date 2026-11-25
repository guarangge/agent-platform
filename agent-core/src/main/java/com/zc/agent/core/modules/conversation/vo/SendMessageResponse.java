package com.zc.agent.core.modules.conversation.vo;

/**
 * 发送消息响应。
 *
 * @param conversation 会话详情
 * @param userMessage  用户消息
 * @param assistantMessage 助手消息
 * @param task         本次任务
 */
public record SendMessageResponse(
        ConversationDetailResponse conversation,
        MessageResponse userMessage,
        MessageResponse assistantMessage,
        TaskResponse task
) {
}
