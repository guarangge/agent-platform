package com.zc.agent.sdk.runtime;

/**
 * 创建异步 Run 的请求。
 *
 * @param content 用户消息正文
 */
public record CreateRuntimeRunRequest(
        String content
) {
}
