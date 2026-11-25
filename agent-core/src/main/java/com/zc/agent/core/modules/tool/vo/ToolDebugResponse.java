package com.zc.agent.core.modules.tool.vo;

import java.util.Map;

/**
 * Tool 调试响应。
 *
 * @param toolCode     Tool 编码
 * @param toolName     Tool 名称
 * @param providerType Provider 类型
 * @param transportType 传输协议
 * @param schemaVersion Schema 版本
 * @param healthStatus 健康状态
 * @param success      是否成功
 * @param message      调试消息
 * @param output       调试输出
 */
public record ToolDebugResponse(
        String toolCode,
        String toolName,
        String providerType,
        String transportType,
        String schemaVersion,
        String healthStatus,
        Boolean success,
        String message,
        Map<String, Object> output
) {
}
