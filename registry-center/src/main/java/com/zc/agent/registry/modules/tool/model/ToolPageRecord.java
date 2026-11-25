package com.zc.agent.registry.modules.tool.model;

import java.time.LocalDateTime;

/**
 * Tool 分页记录。
 *
 * @param toolId          Tool ID
 * @param toolCode        Tool 编码
 * @param toolName        Tool 名称
 * @param toolType        Tool 类型
 * @param toolCategory    Tool 分类
 * @param providerType    Provider 类型
 * @param transportType   传输协议
 * @param permissionLevel 权限等级
 * @param timeoutSeconds  超时时间
 * @param status          状态
 * @param description     描述
 * @param createdAt       创建时间
 */
public record ToolPageRecord(
        Long toolId,
        String toolCode,
        String toolName,
        String toolType,
        String toolCategory,
        String providerType,
        String transportType,
        String permissionLevel,
        Integer timeoutSeconds,
        String status,
        String description,
        LocalDateTime createdAt
) {
}
