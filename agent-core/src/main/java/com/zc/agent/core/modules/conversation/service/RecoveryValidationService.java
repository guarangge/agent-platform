package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 恢复与审批前置校验服务。
 *
 * 负责在进入恢复执行、审批继续执行前，对当前任务状态、审批状态和计划一致性做统一校验。
 */
@Service
public class RecoveryValidationService {

    private static final String STATUS_WAITING_APPROVAL = "waiting_approval";

    private final ObjectMapper objectMapper;

    public RecoveryValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验失败任务是否允许进入通用恢复。
     *
     * @param taskRecord      任务记录
     * @param failedTaskStep  失败步骤
     * @param approvals       当前任务审批记录
     */
    public void validateRecoverableApprovalState(TaskRecord taskRecord,
                                                 TaskStepRecord failedTaskStep,
                                                 List<ApprovalRecord> approvals) {
        boolean hasPendingApproval = approvals.stream().anyMatch(item -> "pending".equals(item.status()));
        if (hasPendingApproval) {
            throw new BusinessException("当前任务仍有待审批节点，请先完成审批，不允许直接恢复执行");
        }
        boolean rejectedByApproval = approvals.stream().anyMatch(item -> "rejected".equals(item.status()));
        if (rejectedByApproval && "workflow".equals(failedTaskStep.stepType())) {
            throw new BusinessException("当前任务已被审批拒绝，不允许通过通用恢复继续执行");
        }
    }

    /**
     * 校验失败步骤与当前执行计划是否仍然一致。
     *
     * @param failedTaskStep 失败步骤
     * @param failedPlanStep 当前计划中对应步骤
     */
    public void validateRecoverablePlan(TaskStepRecord failedTaskStep,
                                        ExecutionPlan.ExecutionPlanStep failedPlanStep) {
        if (!failedTaskStep.stepType().equals(failedPlanStep.stepType())) {
            throw new BusinessException("当前执行计划与失败步骤类型不一致，无法恢复");
        }
        Object snapshot = safeJson(failedTaskStep.inputSnapshot());
        if (snapshot instanceof JsonNode jsonNode) {
            JsonNode currentStepNode = jsonNode.path("currentStep");
            String failedStepId = currentStepNode.path("stepId").asText(null);
            String failedToolCode = currentStepNode.path("targetToolCode").asText(null);
            Long failedWorkflowId = currentStepNode.hasNonNull("targetWorkflowId")
                    ? currentStepNode.path("targetWorkflowId").asLong()
                    : null;
            if (failedStepId != null && !failedStepId.isBlank() && !failedStepId.equals(failedPlanStep.stepId())) {
                throw new BusinessException("当前执行计划与失败步骤标识不一致，无法恢复");
            }
            if (failedToolCode != null && !failedToolCode.isBlank() && !failedToolCode.equals(failedPlanStep.targetToolCode())) {
                throw new BusinessException("当前执行计划的 Tool 已变化，无法直接恢复");
            }
            if (failedWorkflowId != null && !failedWorkflowId.equals(failedPlanStep.targetWorkflowId())) {
                throw new BusinessException("当前执行计划的 Workflow 已变化，无法直接恢复");
            }
        }
    }

    /**
     * 从当前计划中解析运行步骤对应的计划步骤。
     *
     * 自动重规划后，task_steps.step_no 是任务内累计序号，而计划步骤编号会从 1 重新开始，
     * 因此优先使用输入快照中的稳定 stepId 匹配，旧数据缺少 stepId 时再回退到 stepNo。
     *
     * @param taskStep      待恢复的运行步骤
     * @param executionPlan 当前执行计划
     * @return 对应的计划步骤
     */
    public ExecutionPlan.ExecutionPlanStep resolveRecoverablePlanStep(TaskStepRecord taskStep,
                                                                       ExecutionPlan executionPlan) {
        Object snapshot = safeJson(taskStep.inputSnapshot());
        if (snapshot instanceof JsonNode jsonNode) {
            String stepId = jsonNode.path("currentStep").path("stepId").asText(null);
            if (stepId != null && !stepId.isBlank()) {
                return executionPlan.steps().stream()
                        .filter(item -> stepId.equals(item.stepId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("当前执行计划中未找到对应步骤，无法恢复"));
            }
        }
        return executionPlan.steps().stream()
                .filter(item -> item.stepNo().equals(taskStep.stepNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前执行计划中未找到对应步骤，无法恢复"));
    }

    /**
     * 校验审批节点是否仍允许继续执行。
     *
     * @param approvalRecord   审批记录
     * @param approvalTaskStep 审批步骤
     */
    public void validateApprovableState(ApprovalRecord approvalRecord,
                                        TaskStepRecord approvalTaskStep) {
        if (!STATUS_WAITING_APPROVAL.equals(approvalTaskStep.status())) {
            throw new BusinessException("当前审批步骤已不是待审批状态");
        }
        Object snapshot = safeJson(approvalTaskStep.outputSnapshot());
        if (!(snapshot instanceof JsonNode jsonNode)) {
            throw new BusinessException("审批步骤缺少待审批快照，无法继续执行");
        }
        String approvalNodeId = jsonNode.path("approvalNodeId").asText(null);
        String approvalNodeName = jsonNode.path("approvalNodeName").asText(null);
        String approvalStatus = jsonNode.path("approvalStatus").asText(null);
        if (!approvalRecord.nodeId().equals(approvalNodeId) || !approvalRecord.nodeName().equals(approvalNodeName)) {
            throw new BusinessException("审批节点元数据已变化，无法继续执行");
        }
        if (!"pending".equals(approvalStatus)) {
            throw new BusinessException("当前审批节点已不是待审批状态");
        }
    }

    private Object safeJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
