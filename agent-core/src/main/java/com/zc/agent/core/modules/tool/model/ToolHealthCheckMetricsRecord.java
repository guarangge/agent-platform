package com.zc.agent.core.modules.tool.model;

import java.time.LocalDateTime;

/**
 * Tool 健康检查聚合记录。
 */
public record ToolHealthCheckMetricsRecord(
        Long totalCount,
        Long successCount,
        Long failedCount,
        Long avgLatencyMs,
        LocalDateTime lastCheckedAt,
        LocalDateTime lastSuccessAt
) {
}
