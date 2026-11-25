package com.zc.agent.core.modules.tool.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Agent 可用 Tool 定义。
 *
 * @param toolId          Tool ID
 * @param toolCode        Tool 编码
 * @param toolName        Tool 名称
 * @param toolType        Tool 类型
 * @param toolCategory    Tool 分类
 * @param runtimeCapabilities 运行时安全能力声明
 * @param sideEffectLevel 副作用等级
 * @param inputSchema     输入参数 Schema
 * @param outputSchema    输出结果 Schema
 * @param providerType    Provider 类型
 * @param transportType   传输协议
 * @param providerCode    Provider 编码
 * @param providerName    Provider 名称
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode 环境编码
 * @param endpoint        Provider 实例地址
 * @param authType        认证类型
 * @param authConfig      认证配置
 * @param headers         默认请求头
 * @param providerTimeoutSeconds Provider 超时时间
 * @param retryTimes      Provider 重试次数
 * @param operationName   绑定操作名
 * @param operationType   绑定操作类型
 * @param invokeConfig    调用配置
 * @param argumentMapping 参数映射配置
 * @param outputMapping   输出映射配置
 * @param permissionLevel 权限等级
 * @param timeoutSeconds  超时时间（秒）
 * @param status          状态
 * @param description     描述
 * @param metadata        元数据
 */
public record RegistryAgentTool(
        Long toolId,
        String toolCode,
        String toolName,
        String toolType,
        String toolCategory,
        List<String> runtimeCapabilities,
        String sideEffectLevel,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String providerType,
        String transportType,
        String providerCode,
        String providerName,
        String providerInstanceCode,
        String providerInstanceName,
        String environmentCode,
        String endpoint,
        String authType,
        JsonNode authConfig,
        JsonNode headers,
        Integer providerTimeoutSeconds,
        Integer retryTimes,
        String operationName,
        String operationType,
        JsonNode invokeConfig,
        JsonNode argumentMapping,
        JsonNode outputMapping,
        String permissionLevel,
        Integer timeoutSeconds,
        String status,
        String description,
        JsonNode metadata
) {

    /**
     * 兼容升级前的执行快照与插件测试构造方式；缺失副作用等级时运行时从旧 metadata 回退读取。
     */
    public RegistryAgentTool(Long toolId,
                             String toolCode,
                             String toolName,
                             String toolType,
                             String toolCategory,
                             JsonNode inputSchema,
                             JsonNode outputSchema,
                             String providerType,
                             String transportType,
                             String providerCode,
                             String providerName,
                             String providerInstanceCode,
                             String providerInstanceName,
                             String environmentCode,
                             String endpoint,
                             String authType,
                             JsonNode authConfig,
                             JsonNode headers,
                             Integer providerTimeoutSeconds,
                             Integer retryTimes,
                             String operationName,
                             String operationType,
                             JsonNode invokeConfig,
                             JsonNode argumentMapping,
                             JsonNode outputMapping,
                             String permissionLevel,
                             Integer timeoutSeconds,
                             String status,
                             String description,
                             JsonNode metadata) {
        this(toolId, toolCode, toolName, toolType, toolCategory, List.of(), null,
                inputSchema, outputSchema, providerType, transportType, providerCode, providerName,
                providerInstanceCode, providerInstanceName, environmentCode, endpoint, authType,
                authConfig, headers, providerTimeoutSeconds, retryTimes, operationName, operationType,
                invokeConfig, argumentMapping, outputMapping, permissionLevel, timeoutSeconds, status,
                description, metadata);
    }
}
