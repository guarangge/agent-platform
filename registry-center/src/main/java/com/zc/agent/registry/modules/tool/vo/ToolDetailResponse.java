package com.zc.agent.registry.modules.tool.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tool 详情响应。
 *
 * @param toolId          Tool ID
 * @param toolCode        Tool 编码
 * @param toolName        Tool 名称
 * @param toolType        Tool 类型
 * @param toolCategory    Tool 分类
 * @param runtimeCapabilities 运行时安全能力声明
 * @param sideEffectLevel 副作用等级
 * @param description     描述
 * @param inputSchema     输入参数 Schema
 * @param outputSchema    输出结果 Schema
 * @param providerCode    Provider 编码
 * @param providerName    Provider 名称
 * @param providerType    Provider 类型
 * @param transportType   Provider 传输协议
 * @param providerConfigSchema Provider 配置 Schema
 * @param providerStatus  Provider 状态
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode 环境编码
 * @param endpoint        Provider 实例地址
 * @param authType        认证类型
 * @param authConfig      认证配置
 * @param headers         默认请求头
 * @param providerTimeoutSeconds Provider 实例超时时间
 * @param retryTimes      Provider 实例重试次数
 * @param providerInstanceStatus Provider 实例状态
 * @param operationName   绑定操作名
 * @param operationType   绑定操作类型
 * @param invokeConfig    调用配置
 * @param argumentMapping 参数映射配置
 * @param outputMapping   输出映射配置
 * @param bindingStatus   绑定状态
 * @param permissionLevel 权限等级
 * @param timeoutSeconds  超时时间
 * @param status          状态
 * @param metadata        扩展元数据
 * @param remark          备注
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @param createdBy       创建人
 * @param updatedBy       更新人
 */
public record ToolDetailResponse(
        Long toolId,
        String toolCode,
        String toolName,
        String toolType,
        String toolCategory,
        List<String> runtimeCapabilities,
        String sideEffectLevel,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String providerCode,
        String providerName,
        String providerType,
        String transportType,
        JsonNode providerConfigSchema,
        String providerStatus,
        String providerInstanceCode,
        String providerInstanceName,
        String environmentCode,
        String endpoint,
        String authType,
        JsonNode authConfig,
        JsonNode headers,
        Integer providerTimeoutSeconds,
        Integer retryTimes,
        String providerInstanceStatus,
        String operationName,
        String operationType,
        JsonNode invokeConfig,
        JsonNode argumentMapping,
        JsonNode outputMapping,
        String bindingStatus,
        String permissionLevel,
        Integer timeoutSeconds,
        String status,
        JsonNode metadata,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
