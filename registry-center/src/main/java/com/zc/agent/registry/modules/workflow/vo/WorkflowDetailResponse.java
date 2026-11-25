package com.zc.agent.registry.modules.workflow.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Workflow 详情响应。
 *
 * @param workflowId       Workflow ID
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
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param createdBy        创建人
 * @param updatedBy        更新人
 */
public record WorkflowDetailResponse(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String description,
        String version,
        JsonNode definitionJson,
        String status,
        JsonNode metadata,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
