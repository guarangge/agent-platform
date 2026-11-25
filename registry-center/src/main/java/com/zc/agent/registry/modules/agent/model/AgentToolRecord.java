package com.zc.agent.registry.modules.agent.model;

import java.time.LocalDateTime;

/**
 * Agent 已绑定 Tool 记录。
 *
 * @param toolId           Tool ID
 * @param toolCode         Tool 编码
 * @param toolName         Tool 名称
 * @param toolType         Tool 类型
 * @param toolCategory     Tool 分类
 * @param runtimeCapabilitiesJsonText 运行时安全能力声明 JSON
 * @param sideEffectLevel  副作用等级
 * @param inputSchemaJsonText 输入参数 Schema JSON
 * @param outputSchemaJsonText 输出结果 Schema JSON
 * @param providerType     Provider 类型
 * @param transportType    传输协议
 * @param providerCode     Provider 编码
 * @param providerName     Provider 名称
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode  环境编码
 * @param endpoint         Provider 实例地址
 * @param authType         认证类型
 * @param authConfigJsonText 认证配置 JSON
 * @param headersJsonText  默认请求头 JSON
 * @param providerTimeoutSeconds Provider 超时时间
 * @param retryTimes       Provider 重试次数
 * @param operationName    绑定操作名
 * @param operationType    绑定操作类型
 * @param invokeConfigJsonText 调用配置 JSON
 * @param argumentMappingJsonText 参数映射 JSON
 * @param outputMappingJsonText 输出映射 JSON
 * @param permissionLevel  权限等级
 * @param timeoutSeconds   超时时间（秒）
 * @param status           Tool 状态
 * @param description      Tool 描述
 * @param metadataJsonText Tool 元数据 JSON
 * @param boundAt          绑定时间
 */
public record AgentToolRecord(
        Long toolId,
        String toolCode,
        String toolName,
        String toolType,
        String toolCategory,
        String runtimeCapabilitiesJsonText,
        String sideEffectLevel,
        String inputSchemaJsonText,
        String outputSchemaJsonText,
        String providerType,
        String transportType,
        String providerCode,
        String providerName,
        String providerInstanceCode,
        String providerInstanceName,
        String environmentCode,
        String endpoint,
        String authType,
        String authConfigJsonText,
        String headersJsonText,
        Integer providerTimeoutSeconds,
        Integer retryTimes,
        String operationName,
        String operationType,
        String invokeConfigJsonText,
        String argumentMappingJsonText,
        String outputMappingJsonText,
        String permissionLevel,
        Integer timeoutSeconds,
        String status,
        String description,
        String metadataJsonText,
        LocalDateTime boundAt
) {
}
