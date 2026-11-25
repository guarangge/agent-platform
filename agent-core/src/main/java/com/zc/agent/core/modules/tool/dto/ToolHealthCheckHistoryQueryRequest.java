package com.zc.agent.core.modules.tool.dto;

/**
 * Tool 健康检查历史查询请求。
 */
public record ToolHealthCheckHistoryQueryRequest(
        Integer pageNum,
        Integer pageSize
) {
}
