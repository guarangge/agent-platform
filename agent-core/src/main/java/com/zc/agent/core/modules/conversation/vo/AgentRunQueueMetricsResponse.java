package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 持久化运行队列指标。
 *
 * @param statusCounts 各状态数量
 * @param readyCount 当前可领取数量
 * @param leasedCount 有效租约数量
 * @param expiredLeaseCount 已失效租约数量
 * @param oldestQueuedAt 最早排队时间
 * @param queueCapacity 队列容量
 * @param globalConcurrency 全局并发上限
 * @param accepting 当前实例是否接受领取
 */
public record AgentRunQueueMetricsResponse(
        Map<String, Long> statusCounts,
        long readyCount,
        long leasedCount,
        long expiredLeaseCount,
        LocalDateTime oldestQueuedAt,
        int queueCapacity,
        int globalConcurrency,
        boolean accepting
) {
}
