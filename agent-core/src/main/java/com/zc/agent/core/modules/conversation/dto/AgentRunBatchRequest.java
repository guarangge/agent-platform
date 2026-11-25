package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Agent 运行批量入队请求。
 *
 * @param items 待入队消息，单批最多 100 条
 */
public record AgentRunBatchRequest(
        @NotEmpty @Size(max = 100) List<@Valid AgentRunEnqueueItem> items
) {
}
