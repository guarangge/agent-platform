package com.zc.agent.registry.modules.workflow.model;

import java.time.LocalDateTime;

/**
 * Workflow 详情记录。
 *
 * @param workflowId         Workflow ID
 * @param workflowCode       Workflow 编码
 * @param workflowName       Workflow 名称
 * @param workflowType       Workflow 类型
 * @param workflowCategory   Workflow 分类
 * @param description        描述
 * @param version            版本
 * @param definitionJsonText Workflow 定义 JSON 文本
 * @param status             状态
 * @param metadataJsonText   扩展元数据 JSON 文本
 * @param remark             备注
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 * @param createdBy          创建人
 * @param updatedBy          更新人
 */
public record WorkflowDetailRecord(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String description,
        String version,
        String definitionJsonText,
        String status,
        String metadataJsonText,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
