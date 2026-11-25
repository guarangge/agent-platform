package com.zc.agent.sdk.runtime;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 可替换 Transport 接收的不可变 HTTP 请求。
 *
 * @param uri 请求地址
 * @param method HTTP Method
 * @param headers 安全请求头
 * @param body JSON 请求体，可为空
 * @param timeout 请求超时
 */
public record RuntimeHttpRequest(
        URI uri,
        String method,
        Map<String, String> headers,
        String body,
        Duration timeout
) {
}
