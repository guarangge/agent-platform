package com.zc.agent.registry.modules.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 新增模型请求。
 *
 * @param modelCode        模型编码
 * @param modelName        模型名称
 * @param provider         模型供应商
 * @param modelType        模型类型
 * @param baseUrl          基础地址
 * @param apiKey           API Key
 * @param apiPath          API Path
 * @param modelIdentifier  模型标识
 * @param temperature      温度
 * @param maxTokens        最大 Token 数
 * @param contextWindow    上下文窗口大小
 * @param reservedOutputTokens 预留输出 Token 数
 * @param reservedSystemTokens 预留系统 Token 数
 * @param compactionTriggerRatio 触发压缩比例
 * @param tokenizerFamily  分词器族
 * @param timeoutSeconds   超时时间（秒）
 * @param status           状态
 * @param metadata         扩展信息
 * @param remark           备注
 */
public record CreateModelRequest(
        @NotBlank(message = "模型编码不能为空")
        @Size(max = 64, message = "模型编码长度不能超过64个字符")
        String modelCode,
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 128, message = "模型名称长度不能超过128个字符")
        String modelName,
        @NotBlank(message = "模型供应商不能为空")
        @Size(max = 64, message = "模型供应商长度不能超过64个字符")
        String provider,
        @NotBlank(message = "模型类型不能为空")
        @Size(max = 64, message = "模型类型长度不能超过64个字符")
        String modelType,
        @Size(max = 255, message = "基础地址长度不能超过255个字符")
        String baseUrl,
        @Size(max = 500, message = "API Key 长度不能超过500个字符")
        String apiKey,
        @Size(max = 255, message = "API Path 长度不能超过255个字符")
        String apiPath,
        @Size(max = 128, message = "模型标识长度不能超过128个字符")
        String modelIdentifier,
        @DecimalMin(value = "0.0", message = "温度不能小于0")
        @DecimalMax(value = "2.0", message = "温度不能大于2")
        Double temperature,
        @Positive(message = "最大 Token 数必须为正整数")
        Integer maxTokens,
        @Positive(message = "上下文窗口大小必须为正整数")
        Integer contextWindow,
        @Positive(message = "预留输出 Token 数必须为正整数")
        Integer reservedOutputTokens,
        @Positive(message = "预留系统 Token 数必须为正整数")
        Integer reservedSystemTokens,
        @DecimalMin(value = "0.1", message = "触发压缩比例不能小于0.1")
        @DecimalMax(value = "1.0", message = "触发压缩比例不能大于1")
        Double compactionTriggerRatio,
        @Size(max = 64, message = "分词器族长度不能超过64个字符")
        String tokenizerFamily,
        @Positive(message = "超时时间必须为正整数")
        Integer timeoutSeconds,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        JsonNode metadata,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
