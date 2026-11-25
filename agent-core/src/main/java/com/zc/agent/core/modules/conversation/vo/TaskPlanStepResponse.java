package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 任务计划步骤响应。
 *
 * @param taskPlanStepId    计划步骤主键 ID
 * @param stepId            步骤业务 ID
 * @param stepNo            步骤序号
 * @param stepType          步骤类型
 * @param stepName          步骤名称
 * @param objective         步骤目标
 * @param dependsOnJson     依赖步骤 JSON
 * @param targetToolCode    目标 Tool 编码
 * @param targetWorkflowId  目标 Workflow ID
 * @param inputsJson        输入 JSON
 * @param expectedOutput    期望输出
 * @param failurePolicy     失败策略
 * @param replannable       是否允许重规划
 * @param status            状态
 * @param description       步骤说明
 * @param createdAt         创建时间
 */
public record TaskPlanStepResponse(
        Long taskPlanStepId,
        String stepId,
        Integer stepNo,
        String stepType,
        String stepName,
        String objective,
        String dependsOnJson,
        String targetToolCode,
        Long targetWorkflowId,
        String inputsJson,
        String expectedOutput,
        String failurePolicy,
        Boolean replannable,
        String status,
        String description,
        LocalDateTime createdAt
) {
}
