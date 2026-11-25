package com.zc.agent.sdk.runtime;

import java.util.List;
import java.util.Map;

/**
 * Runtime Transport 返回的 HTTP 响应。
 *
 * @param statusCode HTTP 状态码
 * @param headers 响应头
 * @param body 响应正文
 */
public record RuntimeHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body
) {
}
