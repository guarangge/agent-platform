package com.zc.agent.sdk.runtime;

import java.time.Duration;

/**
 * Runtime Event 阻塞消费选项。
 *
 * @param afterSequence 初始游标
 * @param timeout 总等待时限
 * @param reconnectDelay 非终态断连后的重连间隔
 * @param maxEmptyConnections 最大连续空连接数
 */
public record RuntimeEventStreamOptions(
        long afterSequence,
        Duration timeout,
        Duration reconnectDelay,
        int maxEmptyConnections
) {
    public static RuntimeEventStreamOptions defaults() {
        return new RuntimeEventStreamOptions(0, Duration.ofMinutes(15), Duration.ofMillis(300), 3);
    }
}
