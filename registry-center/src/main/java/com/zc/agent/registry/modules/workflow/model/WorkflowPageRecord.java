package com.zc.agent.registry.modules.workflow.model;

import java.time.LocalDateTime;

/**
 * Workflow 分页记录。
 *
 * @param workflowId       Workflow ID
 * @param workflowCode     Workflow 编码
 * @param workflowName     Workflow 名称
 * @param workflowType     Workflow 类型
 * @param workflowCategory Workflow 分类
 * @param version          版本
 * @param status           状态
 * @param description      描述
 * @param createdAt        创建时间
 */
public record WorkflowPageRecord(
        Long workflowId,
        String workflowCode,
        String workflowName,
        String workflowType,
        String workflowCategory,
        String version,
        String status,
        String description,
        LocalDateTime createdAt
) {
}
