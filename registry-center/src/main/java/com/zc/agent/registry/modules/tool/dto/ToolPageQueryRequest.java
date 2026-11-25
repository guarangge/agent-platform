package com.zc.agent.registry.modules.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Tool 分页查询请求。
 *
 * @param pageNum       页码
 * @param pageSize      每页条数
 * @param toolCode      Tool 编码
 * @param toolName      Tool 名称
 * @param toolType      Tool 类型
 * @param toolCategory  Tool 分类
 * @param status        Tool 状态
 */
public record ToolPageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "Tool 编码长度不能超过64个字符")
        String toolCode,
        @Size(max = 128, message = "Tool 名称长度不能超过128个字符")
        String toolName,
        @Size(max = 64, message = "Tool 类型长度不能超过64个字符")
        String toolType,
        @Size(max = 64, message = "Tool 分类长度不能超过64个字符")
        String toolCategory,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
