package com.zc.agent.registry.modules.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 模型分页查询请求。
 *
 * @param pageNum   页码
 * @param pageSize  每页条数
 * @param modelCode 模型编码
 * @param modelName 模型名称
 * @param provider  模型供应商
 * @param modelType 模型类型
 * @param status    状态
 */
public record ModelPageQueryRequest(
        @Min(value = 1, message = "页码不能小于1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数不能小于1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "模型编码长度不能超过64个字符")
        String modelCode,
        @Size(max = 128, message = "模型名称长度不能超过128个字符")
        String modelName,
        @Size(max = 64, message = "模型供应商长度不能超过64个字符")
        String provider,
        @Size(max = 64, message = "模型类型长度不能超过64个字符")
        String modelType,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
