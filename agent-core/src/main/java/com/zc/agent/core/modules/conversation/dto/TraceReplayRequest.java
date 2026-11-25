package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * Trace Prompt 回放请求。
 *
 * @param modelCode   指定回放模型编码，为空时使用原 Agent 当前默认模型
 * @param systemPrompt 可选 System Prompt 覆盖值
 * @param userPrompt   可选 User Prompt 覆盖值
 */
public record TraceReplayRequest(
        @Size(max = 128) String modelCode,
        @Size(max = 100_000) String systemPrompt,
        @Size(max = 500_000) String userPrompt
) {
}
