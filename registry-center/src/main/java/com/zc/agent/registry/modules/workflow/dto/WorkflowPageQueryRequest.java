package com.zc.agent.registry.modules.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Workflow 分页查询请求。
 *
 * @param pageNum          页码
 * @param pageSize         每页条数
 * @param workflowCode     Workflow 编码
 * @param workflowName     Workflow 名称
 * @param workflowType     Workflow 类型
 * @param workflowCategory Workflow 分类
 * @param version          版本
 * @param status           状态
 */
public record WorkflowPageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "Workflow 编码长度不能超过64个字符")
        String workflowCode,
        @Size(max = 128, message = "Workflow 名称长度不能超过128个字符")
        String workflowName,
        @Size(max = 64, message = "Workflow 类型长度不能超过64个字符")
        String workflowType,
        @Size(max = 64, message = "Workflow 分类长度不能超过64个字符")
        String workflowCategory,
        @Size(max = 32, message = "版本长度不能超过32个字符")
        String version,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
