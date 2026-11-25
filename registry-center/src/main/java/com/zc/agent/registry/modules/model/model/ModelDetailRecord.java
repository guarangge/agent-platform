package com.zc.agent.registry.modules.model.model;

import java.time.LocalDateTime;

/**
 * 模型详情记录。
 *
 * @param modelId           模型 ID
 * @param modelCode         模型编码
 * @param modelName         模型名称
 * @param provider          模型供应商
 * @param modelType         模型类型
 * @param baseUrl           基础地址
 * @param apiKey            API Key
 * @param apiPath           API Path
 * @param modelIdentifier   模型标识
 * @param temperature       温度
 * @param maxTokens         最大 Token 数
 * @param contextWindow     上下文窗口大小
 * @param reservedOutputTokens 预留输出 Token 数
 * @param reservedSystemTokens 预留系统 Token 数
 * @param compactionTriggerRatio 触发压缩比例
 * @param tokenizerFamily   分词器族
 * @param timeoutSeconds    超时时间（秒）
 * @param status            状态
 * @param metadataJsonText  元数据 JSON
 * @param remark            备注
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 * @param createdBy         创建人
 * @param updatedBy         更新人
 */
public record ModelDetailRecord(
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
        String metadataJsonText,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
