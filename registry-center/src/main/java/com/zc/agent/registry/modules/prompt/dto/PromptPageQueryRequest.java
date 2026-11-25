package com.zc.agent.registry.modules.prompt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Prompt 分页查询请求。
 *
 * @param pageNum         页码
 * @param pageSize        每页条数
 * @param promptCode      Prompt 编码
 * @param promptName      Prompt 名称
 * @param promptType      Prompt 类型
 * @param promptCategory  Prompt 分类
 * @param status          状态
 */
public record PromptPageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "Prompt 编码长度不能超过64个字符")
        String promptCode,
        @Size(max = 128, message = "Prompt 名称长度不能超过128个字符")
        String promptName,
        @Size(max = 64, message = "Prompt 类型长度不能超过64个字符")
        String promptType,
        @Size(max = 64, message = "Prompt 分类长度不能超过64个字符")
        String promptCategory,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
