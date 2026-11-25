package com.zc.agent.core.modules.runtime.model;

import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;

import java.util.List;

/**
 * Agent Loop 和 Orchestrator 共享的稳定执行请求。
 *
 * @param executionContext 运行时上下文
 * @param executionDecision 执行决策
 * @param executionPlan 执行计划
 * @param taskId Task ID
 * @param traceId Trace ID
 * @param inputSnapshot 基础输入快照
 * @param authorization 原始授权头
 * @param operatorId 操作人 ID
 * @param startStepNo 起始计划步骤序号
 * @param stepNoOffset 持久化步骤序号偏移量
 * @param recoveredStepResults 已恢复的前序步骤结果
 * @param recoveryReplaySnapshot 普通恢复回放快照
 * @param approvalReplaySnapshot 审批恢复回放快照
 * @param workflowResumeContext Workflow 恢复上下文
 */
public record RuntimeExecutionRequest(
        ExecutionContext executionContext,
        ExecutionDecision executionDecision,
        ExecutionPlan executionPlan,
        Long taskId,
        String traceId,
        String inputSnapshot,
        String authorization,
        Long operatorId,
        Integer startStepNo,
        Integer stepNoOffset,
        List<RuntimeStepResultContext> recoveredStepResults,
        RecoveryReplaySnapshot recoveryReplaySnapshot,
        ApprovalReplaySnapshot approvalReplaySnapshot,
        WorkflowResumeContext workflowResumeContext
) {

    /**
     * 普通恢复回放快照。
     *
     * @param resumed 是否已恢复
     * @param recoveredFromTaskStepId 恢复来源 Task Step ID
     * @param recoveredFromStepNo 恢复来源步骤序号
     * @param recoveredFromStepName 恢复来源步骤名称
     * @param replayedContext 已回放上下文摘要
     */
    public record RecoveryReplaySnapshot(
            boolean resumed,
            Long recoveredFromTaskStepId,
            Integer recoveredFromStepNo,
            String recoveredFromStepName,
            List<String> replayedContext
    ) {
    }

    /**
     * 审批恢复回放快照。
     *
     * @param approvalId Approval ID
     * @param approvalNodeId 审批节点 ID
     * @param approvalNodeName 审批节点名称
     * @param approvalComment 审批说明
     * @param approvalStatus 审批状态
     * @param toolCallId Capability 审批前生成的 Tool Call ID
     * @param parameterHash Capability 审批绑定的参数哈希
     */
    public record ApprovalReplaySnapshot(
            Long approvalId,
            String approvalNodeId,
            String approvalNodeName,
            String approvalComment,
            String approvalStatus,
            String toolCallId,
            String parameterHash
    ) {
        /** 已批准的同一步骤 Guardrail 或 Capability 节点可跳过一次前置 Guardrail，防止恢复后重复审批。 */
        public boolean approvesPlanStep(ExecutionPlan.ExecutionPlanStep step, Integer startStepNo) {
            return step != null
                    && step.stepNo() != null
                    && step.stepNo().equals(startStepNo)
                    && "approved".equals(approvalStatus)
                    && (("guardrail:" + step.stepId()).equals(approvalNodeId)
                    || (approvalNodeId != null && approvalNodeId.startsWith("capability:")));
        }
    }
}
