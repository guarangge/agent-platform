package com.zc.agent.sdk.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Runtime Transport 返回的流式或二进制响应。
 *
 * @param statusCode HTTP 状态码
 * @param headers 响应头
 * @param body 响应流，由调用方关闭
 */
public record RuntimeHttpStreamResponse(
        int statusCode,
        Map<String, List<String>> headers,
        InputStream body
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        body.close();
    }
}
