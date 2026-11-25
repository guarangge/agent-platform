package com.zc.agent.core.modules.workflow.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 可用 Workflow 摘要。
 *
 * @param workflowId        Workflow ID
 * @param workflowCode      Workflow 编码
 * @param workflowName      Workflow 名称
 * @param workflowType      Workflow 类型
 * @param workflowCategory  Workflow 分类
 * @param status            Workflow 状态
 * @param description       Workflow 描述
 * @param metadata          Workflow 元数据
 */
public record RegistryAgentWorkflow(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String status,
        String description,
        JsonNode metadata
) {
}
