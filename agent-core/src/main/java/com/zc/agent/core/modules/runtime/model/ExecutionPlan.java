package com.zc.agent.core.modules.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * 一次执行的可落地执行计划。
 *
 * @param planId   计划 ID
 * @param planType 计划类型
 * @param goal     计划目标
 * @param status   计划状态
 * @param assumptions 规划假设
 * @param constraints 规划约束
 * @param risks    规划风险
 * @param steps    计划步骤列表
 */
public record ExecutionPlan(
        String planId,
        String planType,
        String goal,
        String status,
        List<String> assumptions,
        List<String> constraints,
        List<String> risks,
        List<ExecutionPlanStep> steps
) {

    /**
     * 返回首个计划步骤。
     *
     * @return 首个步骤
     */
    public ExecutionPlanStep firstStep() {
        return steps.get(0);
    }

    /**
     * 计划步骤定义。
     *
     * @param stepNo            步骤序号
     * @param stepId            步骤 ID
     * @param stepType          步骤类型
     * @param stepName          步骤名称
     * @param objective         步骤目标
     * @param dependsOnStepIds  依赖步骤 ID 列表
     * @param targetToolCode    目标 Tool 编码
     * @param targetWorkflowId  目标 Workflow ID
     * @param inputs            步骤输入
     * @param expectedOutput    期望输出
     * @param failurePolicy     失败策略
     * @param replannable       是否允许重规划
     * @param status            步骤状态
     * @param description       步骤说明
     */
    public record ExecutionPlanStep(
            Integer stepNo,
            String stepId,
            String stepType,
            String stepName,
            String objective,
            List<String> dependsOnStepIds,
            String targetToolCode,
            Long targetWorkflowId,
            Map<String, Object> inputs,
            String expectedOutput,
            String failurePolicy,
            Boolean replannable,
            String status,
            String description
    ) {
    }
}
