package com.zc.agent.core.modules.tool.vo;

import java.time.LocalDateTime;

/**
 * Tool 健康检查历史响应。
 */
public record ToolHealthCheckHistoryResponse(
        Long toolHealthCheckId,
        Long toolId,
        String toolCode,
        String toolName,
        String providerType,
        String transportType,
        String providerCode,
        String providerName,
        String providerInstanceCode,
        String providerInstanceName,
        String environmentCode,
        String endpoint,
        String operationName,
        String operationType,
        String healthStatus,
        Boolean success,
        String message,
        String failureReason,
        Integer statusCode,
        Long latencyMs,
        String outputSnapshot,
        LocalDateTime checkedAt
) {
}
