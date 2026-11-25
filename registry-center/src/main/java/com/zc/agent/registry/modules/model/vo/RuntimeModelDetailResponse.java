package com.zc.agent.registry.modules.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 仅供 agent-core 读取的模型运行时配置。
 *
 * @param modelId 模型 ID
 * @param modelCode 模型编码
 * @param modelName 模型名称
 * @param provider 模型供应商
 * @param modelType 模型类型
 * @param baseUrl 基础地址
 * @param apiKey 解密后的运行时 API Key
 * @param apiPath API Path
 * @param modelIdentifier 供应商侧模型标识
 * @param temperature 温度
 * @param maxTokens 最大输出 Token 数
 * @param contextWindow 上下文窗口
 * @param reservedOutputTokens 预留输出 Token 数
 * @param reservedSystemTokens 预留系统 Token 数
 * @param compactionTriggerRatio 压缩触发比例
 * @param tokenizerFamily Tokenizer 类型
 * @param timeoutSeconds 超时秒数
 * @param status 状态
 * @param metadata 扩展信息
 * @param remark 备注
 */
public record RuntimeModelDetailResponse(
        Long modelId,
        String modelCode,
        String modelName,
        String provider,
        String modelType,
        String baseUrl,
        String apiKey,
        String apiPath,
        String modelIdentifier,
        Double temperature,
        Integer maxTokens,
        Integer contextWindow,
        Integer reservedOutputTokens,
        Integer reservedSystemTokens,
        Double compactionTriggerRatio,
        String tokenizerFamily,
        Integer timeoutSeconds,
        String status,
        JsonNode metadata,
        String remark
) {
}
