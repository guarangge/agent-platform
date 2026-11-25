package com.zc.agent.registry.modules.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编辑 Workflow 请求。
 *
 * @param workflowCode     Workflow 编码
 * @param workflowName     Workflow 名称
 * @param workflowType     Workflow 类型
 * @param workflowCategory Workflow 分类
 * @param description      描述
 * @param version          版本
 * @param definitionJson   Workflow 定义 JSON
 * @param status           状态
 * @param metadata         扩展元数据
 * @param remark           备注
 */
public record UpdateWorkflowRequest(
        @NotBlank(message = "Workflow 编码不能为空")
        @Size(max = 64, message = "Workflow 编码长度不能超过64个字符")
        String workflowCode,
        @NotBlank(message = "Workflow 名称不能为空")
        @Size(max = 128, message = "Workflow 名称长度不能超过128个字符")
        String workflowName,
        @NotBlank(message = "Workflow 类型不能为空")
        @Size(max = 64, message = "Workflow 类型长度不能超过64个字符")
        String workflowType,
        @NotBlank(message = "Workflow 分类不能为空")
        @Size(max = 64, message = "Workflow 分类长度不能超过64个字符")
        String workflowCategory,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @NotBlank(message = "版本不能为空")
        @Size(max = 32, message = "版本长度不能超过32个字符")
        String version,
        @NotNull(message = "Workflow 定义不能为空")
        JsonNode definitionJson,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        JsonNode metadata,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
