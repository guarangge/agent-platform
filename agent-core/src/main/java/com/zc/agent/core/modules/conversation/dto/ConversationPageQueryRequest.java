package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 会话分页查询请求。
 *
 * @param pageNum  页码
 * @param pageSize 每页条数
 * @param agentId  Agent ID
 */
public record ConversationPageQueryRequest(
        @Min(value = 1, message = "页码最小为 1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为 1")
        @Max(value = 100, message = "每页条数最大为 100")
        Integer pageSize,
        @Positive(message = "Agent ID 必须为正整数")
        Long agentId
) {
}
