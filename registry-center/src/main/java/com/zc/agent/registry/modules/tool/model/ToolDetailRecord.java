package com.zc.agent.registry.modules.tool.model;

import java.time.LocalDateTime;

/**
 * Tool 详情记录。
 *
 * @param toolId           Tool ID
 * @param toolCode         Tool 编码
 * @param toolName         Tool 名称
 * @param toolType         Tool 类型
 * @param toolCategory     Tool 分类
 * @param runtimeCapabilitiesJson 运行时安全能力声明 JSON
 * @param sideEffectLevel  副作用等级
 * @param permissionLevel  权限等级
 * @param timeoutSeconds   超时时间
 * @param status           状态
 * @param description      描述
 * @param inputSchemaJson  输入参数 Schema JSON
 * @param outputSchemaJson 输出结果 Schema JSON
 * @param providerCode     Provider 编码
 * @param providerName     Provider 名称
 * @param providerType     Provider 类型
 * @param transportType    Provider 传输协议
 * @param providerConfigSchemaJson Provider 配置 Schema JSON
 * @param providerStatus   Provider 状态
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode  环境编码
 * @param endpoint         Provider 实例地址
 * @param authType         认证类型
 * @param authConfigJson   认证配置 JSON
 * @param headersJson      默认请求头 JSON
 * @param providerTimeoutSeconds Provider 实例超时时间
 * @param retryTimes       Provider 实例重试次数
 * @param providerInstanceStatus Provider 实例状态
 * @param operationName    绑定操作名
 * @param operationType    绑定操作类型
 * @param invokeConfigJson 调用配置 JSON
 * @param argumentMappingJson 参数映射 JSON
 * @param outputMappingJson 输出映射 JSON
 * @param bindingStatus    绑定状态
 * @param metadataJson     扩展元数据 JSON
 * @param remark           备注
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param createdBy        创建人
 * @param updatedBy        更新人
 */
public record ToolDetailRecord(
        Long toolId,
        String toolCode,
        String toolName,
        String toolType,
        String toolCategory,
        String runtimeCapabilitiesJson,
        String sideEffectLevel,
        String permissionLevel,
        Integer timeoutSeconds,
        String status,
        String description,
        String inputSchemaJson,
        String outputSchemaJson,
        String providerCode,
        String providerName,
        String providerType,
        String transportType,
        String providerConfigSchemaJson,
        String providerStatus,
        String providerInstanceCode,
        String providerInstanceName,
        String environmentCode,
        String endpoint,
        String authType,
        String authConfigJson,
        String headersJson,
        Integer providerTimeoutSeconds,
        Integer retryTimes,
        String providerInstanceStatus,
        String operationName,
        String operationType,
        String invokeConfigJson,
        String argumentMappingJson,
        String outputMappingJson,
        String bindingStatus,
        String metadataJson,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
