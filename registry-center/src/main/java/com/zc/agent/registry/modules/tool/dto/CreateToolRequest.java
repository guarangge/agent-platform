package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增 Tool 请求。
 *
 * @param toolCode         Tool 编码
 * @param toolName         Tool 名称
 * @param toolType         Tool 类型
 * @param toolCategory     Tool 分类
 * @param runtimeCapabilities 运行时安全能力声明
 * @param sideEffectLevel  副作用等级
 * @param description      Tool 描述
 * @param inputSchema      输入参数 Schema
 * @param outputSchema     输出结果 Schema
 * @param providerCode     Provider 编码
 * @param providerName     Provider 名称
 * @param providerType     Provider 类型
 * @param transportType    Provider 传输协议
 * @param providerConfigSchema Provider 配置 Schema
 * @param providerStatus   Provider 状态
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode  环境编码
 * @param endpoint         Provider 实例地址
 * @param authType         认证类型
 * @param authConfig       认证配置
 * @param headers          默认请求头
 * @param providerTimeoutSeconds Provider 实例超时时间
 * @param retryTimes       Provider 实例重试次数
 * @param providerInstanceStatus Provider 实例状态
 * @param operationName    绑定操作名
 * @param operationType    绑定操作类型
 * @param invokeConfig     调用配置
 * @param argumentMapping  参数映射配置
 * @param outputMapping    输出映射配置
 * @param bindingStatus    绑定状态
 * @param permissionLevel  权限等级
 * @param timeoutSeconds   超时时间（秒）
 * @param status           Tool 状态
 * @param metadata         扩展元数据
 * @param remark           备注
 */
public record CreateToolRequest(
        @NotBlank(message = "Tool 编码不能为空")
        @Size(max = 64, message = "Tool 编码长度不能超过64个字符")
        String toolCode,
        @NotBlank(message = "Tool 名称不能为空")
        @Size(max = 128, message = "Tool 名称长度不能超过128个字符")
        String toolName,
        @NotBlank(message = "Tool 类型不能为空")
        @Size(max = 64, message = "Tool 类型长度不能超过64个字符")
        String toolType,
        @NotBlank(message = "Tool 分类不能为空")
        @Size(max = 64, message = "Tool 分类长度不能超过64个字符")
        String toolCategory,
        @NotNull(message = "运行时能力声明不能为空")
        @Size(max = 9, message = "运行时能力声明不能超过9项")
        List<String> runtimeCapabilities,
        @NotBlank(message = "副作用等级不能为空")
        @Size(max = 32, message = "副作用等级长度不能超过32个字符")
        String sideEffectLevel,
        @Size(max = 500, message = "Tool 描述长度不能超过500个字符")
        String description,
        @NotNull(message = "输入参数 Schema 不能为空")
        JsonNode inputSchema,
        @NotNull(message = "输出结果 Schema 不能为空")
        JsonNode outputSchema,
        @NotBlank(message = "Provider 编码不能为空")
        @Size(max = 64, message = "Provider 编码长度不能超过64个字符")
        String providerCode,
        @NotBlank(message = "Provider 名称不能为空")
        @Size(max = 128, message = "Provider 名称长度不能超过128个字符")
        String providerName,
        @NotBlank(message = "Provider 类型不能为空")
        @Size(max = 64, message = "Provider 类型长度不能超过64个字符")
        String providerType,
        @NotBlank(message = "传输协议不能为空")
        @Size(max = 64, message = "传输协议长度不能超过64个字符")
        String transportType,
        @NotNull(message = "Provider 配置 Schema 不能为空")
        JsonNode providerConfigSchema,
        @NotBlank(message = "Provider 状态不能为空")
        @Size(max = 32, message = "Provider 状态长度不能超过32个字符")
        String providerStatus,
        @NotBlank(message = "Provider 实例编码不能为空")
        @Size(max = 64, message = "Provider 实例编码长度不能超过64个字符")
        String providerInstanceCode,
        @NotBlank(message = "Provider 实例名称不能为空")
        @Size(max = 128, message = "Provider 实例名称长度不能超过128个字符")
        String providerInstanceName,
        @NotBlank(message = "环境编码不能为空")
        @Size(max = 64, message = "环境编码长度不能超过64个字符")
        String environmentCode,
        @Size(max = 500, message = "Provider 实例地址长度不能超过500个字符")
        String endpoint,
        @NotBlank(message = "认证类型不能为空")
        @Size(max = 64, message = "认证类型长度不能超过64个字符")
        String authType,
        @NotNull(message = "认证配置不能为空")
        JsonNode authConfig,
        @NotNull(message = "默认请求头不能为空")
        JsonNode headers,
        @NotNull(message = "Provider 实例超时时间不能为空")
        @Min(value = 1, message = "Provider 实例超时时间必须大于0秒")
        Integer providerTimeoutSeconds,
        @NotNull(message = "Provider 实例重试次数不能为空")
        @Min(value = 0, message = "Provider 实例重试次数不能小于0")
        Integer retryTimes,
        @NotBlank(message = "Provider 实例状态不能为空")
        @Size(max = 32, message = "Provider 实例状态长度不能超过32个字符")
        String providerInstanceStatus,
        @NotBlank(message = "绑定操作名不能为空")
        @Size(max = 128, message = "绑定操作名长度不能超过128个字符")
        String operationName,
        @NotBlank(message = "绑定操作类型不能为空")
        @Size(max = 64, message = "绑定操作类型长度不能超过64个字符")
        String operationType,
        @NotNull(message = "调用配置不能为空")
        JsonNode invokeConfig,
        @NotNull(message = "参数映射配置不能为空")
        JsonNode argumentMapping,
        @NotNull(message = "输出映射配置不能为空")
        JsonNode outputMapping,
        @NotBlank(message = "绑定状态不能为空")
        @Size(max = 32, message = "绑定状态长度不能超过32个字符")
        String bindingStatus,
        @NotBlank(message = "权限等级不能为空")
        @Size(max = 32, message = "权限等级长度不能超过32个字符")
        String permissionLevel,
        @NotNull(message = "超时时间不能为空")
        @Min(value = 1, message = "超时时间必须大于0秒")
        Integer timeoutSeconds,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        JsonNode metadata,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
