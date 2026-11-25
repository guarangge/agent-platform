package com.zc.agent.registry.modules.agent.vo;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 已绑定 Tool 响应。
 *
 * @param toolId           Tool ID
 * @param toolCode         Tool 编码
 * @param toolName         Tool 名称
 * @param toolType         Tool 类型
 * @param toolCategory     Tool 分类
 * @param runtimeCapabilities 运行时安全能力声明
 * @param sideEffectLevel  副作用等级
 * @param inputSchema      输入参数 Schema
 * @param outputSchema     输出结果 Schema
 * @param providerType     Provider 类型
 * @param transportType    传输协议
 * @param providerCode     Provider 编码
 * @param providerName     Provider 名称
 * @param providerInstanceCode Provider 实例编码
 * @param providerInstanceName Provider 实例名称
 * @param environmentCode  环境编码
 * @param endpoint         Provider 实例地址
 * @param authType         认证类型
 * @param authConfig       认证配置
 * @param headers          默认请求头
 * @param providerTimeoutSeconds Provider 超时时间
 * @param retryTimes       Provider 重试次数
 * @param operationName    绑定操作名
 * @param operationType    绑定操作类型
 * @param invokeConfig     调用配置
 * @param argumentMapping  参数映射配置
 * @param outputMapping    输出映射配置
 * @param permissionLevel  权限等级
 * @param timeoutSeconds   超时时间（秒）
 * @param status           Tool 状态
 * @param description      Tool 描述
 * @param metadata         Tool 元数据
 * @param boundAt          绑定时间
 */
public record AgentToolResponse(
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
        JsonNode metadata,
        LocalDateTime boundAt
) {
}
