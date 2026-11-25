package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 任务运行中心分页查询条件。
 *
 * @param pageNum        页码
 * @param pageSize       每页条数
 * @param status         任务状态
 * @param agentId        Agent ID
 * @param conversationId 会话 ID
 * @param taskId         任务 ID
 * @param keyword        任务编码、会话编码或会话标题关键字
 */
public record TaskOperationsPageQueryRequest(
        @Min(value = 1, message = "页码最小为 1") Integer pageNum,
        @Min(value = 1, message = "每页条数最小为 1")
        @Max(value = 100, message = "每页条数最大为 100") Integer pageSize,
        String status,
        @Positive(message = "Agent ID 必须为正整数") Long agentId,
        @Positive(message = "会话 ID 必须为正整数") Long conversationId,
        @Positive(message = "任务 ID 必须为正整数") Long taskId,
        String keyword
) {
}
