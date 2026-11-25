package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import org.springframework.stereotype.Service;

/**
 * 审批与恢复状态机服务。
 *
 * 负责统一维护 resume / approve / reject 入口的状态边界，避免状态判断散落在会话服务中。
 */
@Service
public class ApprovalRecoveryStateMachineService {

    private static final String TASK_STATUS_FAILED = "failed";
    private static final String TASK_STATUS_PAUSED = "paused";
    private static final String TASK_STATUS_WAITING_APPROVAL = "waiting_approval";
    private static final String APPROVAL_STATUS_PENDING = "pending";
    private static final String TASK_STEP_STATUS_FAILED = "failed";
    private static final String TASK_STEP_STATUS_PAUSED = "paused";
    private static final String TASK_STEP_STATUS_WAITING_APPROVAL = "waiting_approval";

    /**
     * 校验任务是否允许进入通用恢复。
     *
     * @param taskRecord 任务记录
     */
    public void validateTaskCanResume(TaskRecord taskRecord) {
        if (!TASK_STATUS_FAILED.equals(taskRecord.status()) && !TASK_STATUS_PAUSED.equals(taskRecord.status())) {
            throw new BusinessException("当前任务不是失败或暂停状态，无法恢复执行");
        }
    }

    /**
     * 校验恢复目标步骤是否仍是失败步骤。
     *
     * @param failedTaskStep 失败步骤
     */
    public void validateFailedRecoveryStep(TaskStepRecord failedTaskStep) {
        if (!TASK_STEP_STATUS_FAILED.equals(failedTaskStep.status()) && !TASK_STEP_STATUS_PAUSED.equals(failedTaskStep.status())) {
            throw new BusinessException("当前恢复步骤状态已变化，无法恢复执行");
        }
    }

    /**
     * 校验任务是否允许审批通过后继续执行。
     *
     * @param taskRecord 任务记录
     */
    public void validateTaskCanApprove(TaskRecord taskRecord) {
        if (!TASK_STATUS_WAITING_APPROVAL.equals(taskRecord.status())) {
            throw new BusinessException("当前任务不处于待审批状态");
        }
    }

    /**
     * 校验审批通过动作的审批状态。
     *
     * @param approvalRecord   审批记录
     * @param approvalTaskStep 审批步骤
     */
    public void validateApprovalCanApprove(ApprovalRecord approvalRecord,
                                           TaskStepRecord approvalTaskStep) {
        validateApprovalPending(approvalRecord, approvalTaskStep);
    }

    /**
     * 校验任务是否允许审批拒绝。
     *
     * @param taskRecord 任务记录
     */
    public void validateTaskCanReject(TaskRecord taskRecord) {
        if (!TASK_STATUS_WAITING_APPROVAL.equals(taskRecord.status())) {
            throw new BusinessException("当前任务不处于待审批状态");
        }
    }

    /**
     * 校验审批拒绝动作的审批状态。
     *
     * @param approvalRecord   审批记录
     * @param approvalTaskStep 审批步骤
     */
    public void validateApprovalCanReject(ApprovalRecord approvalRecord,
                                          TaskStepRecord approvalTaskStep) {
        validateApprovalPending(approvalRecord, approvalTaskStep);
    }

    private void validateApprovalPending(ApprovalRecord approvalRecord,
                                         TaskStepRecord approvalTaskStep) {
        if (!APPROVAL_STATUS_PENDING.equals(approvalRecord.status())) {
            throw new BusinessException("当前审批记录已不是待审批状态");
        }
        if (!TASK_STEP_STATUS_WAITING_APPROVAL.equals(approvalTaskStep.status())) {
            throw new BusinessException("当前审批步骤已不是待审批状态");
        }
    }
}
