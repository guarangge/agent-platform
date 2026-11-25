package com.zc.agent.sdk.runtime;

/**
 * 创建 Thread 的基础请求。
 *
 * @param agentId Agent ID
 * @param title Thread 标题，可为空
 */
public record CreateRuntimeThreadRequest(
        long agentId,
        String title
) {
}
