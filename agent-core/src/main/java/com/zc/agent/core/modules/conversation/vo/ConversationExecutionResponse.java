package com.zc.agent.core.modules.conversation.vo;

import java.util.List;

/**
 * 会话执行记录响应。
 *
 * @param conversationId 会话 ID
 * @param tasks          任务列表
 */
public record ConversationExecutionResponse(
        Long conversationId,
        List<TaskResponse> tasks
) {
}
