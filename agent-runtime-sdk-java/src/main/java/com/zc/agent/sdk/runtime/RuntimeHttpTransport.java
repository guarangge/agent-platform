package com.zc.agent.sdk.runtime;

import java.io.IOException;

/** SDK HTTP 传输扩展点，可替换为企业网关或测试 Transport。 */
@FunctionalInterface
public interface RuntimeHttpTransport {

    RuntimeHttpResponse execute(RuntimeHttpRequest request) throws IOException, InterruptedException;

    /** 打开 SSE 或二进制响应；自定义 Transport 使用相关能力时应覆盖本方法。 */
    default RuntimeHttpStreamResponse openStream(RuntimeHttpRequest request) throws IOException, InterruptedException {
        throw new IOException("当前 RuntimeHttpTransport 不支持流式响应");
    }
}
