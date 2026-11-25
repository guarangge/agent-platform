package com.zc.agent.registry.modules.agent.model;

import java.time.LocalDateTime;

/**
 * Agent 已绑定 Workflow 记录。
 *
 * @param workflowId        Workflow ID
 * @param workflowCode      Workflow 编码
 * @param workflowName      Workflow 名称
 * @param workflowType      Workflow 类型
 * @param workflowCategory  Workflow 分类
 * @param status            Workflow 状态
 * @param description       Workflow 描述
 * @param metadataJsonText  Workflow 元数据 JSON
 * @param boundAt           绑定时间
 */
public record AgentWorkflowRecord(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String status,
        String description,
        String metadataJsonText,
        LocalDateTime boundAt
) {
}
