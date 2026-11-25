package com.zc.agent.registry.modules.model.model;

import java.time.LocalDateTime;

/**
 * 模型分页记录。
 *
 * @param modelId     模型 ID
 * @param modelCode   模型编码
 * @param modelName   模型名称
 * @param provider    模型供应商
 * @param modelType   模型类型
 * @param status      状态
 * @param createdAt   创建时间
 */
public record ModelPageRecord(
        Long modelId,
        String modelCode,
        String modelName,
        String provider,
        String modelType,
        String status,
        LocalDateTime createdAt
) {
}
