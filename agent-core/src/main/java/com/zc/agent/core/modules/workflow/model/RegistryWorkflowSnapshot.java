package com.zc.agent.core.modules.workflow.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 注册中心 Workflow 快照。
 *
 * @param workflowId       Workflow ID
 * @param workflowCode     Workflow 编码
 * @param workflowName     Workflow 名称
 * @param workflowType     Workflow 类型
 * @param workflowCategory Workflow 分类
 * @param status           Workflow 状态
 * @param definitionJson   Workflow 定义 JSON
 * @param metadata         Workflow 扩展元数据
 */
public record RegistryWorkflowSnapshot(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String status,
        JsonNode definitionJson,
        JsonNode metadata
) {
}
