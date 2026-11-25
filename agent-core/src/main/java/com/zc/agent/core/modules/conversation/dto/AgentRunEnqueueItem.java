package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 单条 Agent 运行入队参数。
 *
 * @param content 用户输入
 * @param idempotencyKey 幂等键
 * @param priority 优先级，数值越大越优先
 */
public record AgentRunEnqueueItem(
        @NotBlank @Size(max = 20000) String content,
        @Size(max = 128) String idempotencyKey,
        Integer priority
) {
}
