package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Thread 的最小稳定响应字段。
 *
 * @param conversationId Thread ID
 * @param conversationCode Thread 编码
 * @param agentId Agent ID
 * @param title Thread 标题
 * @param status Thread 状态
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeThread(
        Long conversationId,
        String conversationCode,
        Long agentId,
        String title,
        String status
) {
}
