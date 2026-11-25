package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 计划级审批步骤服务。
 */
@Service
public class ApprovalStepService {

    /**
     * 执行 approval 步骤，统一转为审批挂起异常。
     *
     * @param step 当前计划步骤
     */
    public void execute(ExecutionPlan.ExecutionPlanStep step) {
        Integer nextStepNo = step.stepNo() == null ? null : step.stepNo() + 1;
        throw new ApprovalRequiredException(
                "当前计划等待审批",
                step.stepId(),
                step.stepName(),
                null,
                List.of(),
                List.of(),
                "plan_step",
                nextStepNo
        );
    }

    /**
     * 构造计划级审批输出快照元数据。
     *
     * @param step 当前步骤
     * @return 元数据
     */
    public Map<String, Object> buildPendingMetadata(ExecutionPlan.ExecutionPlanStep step) {
        return Map.of(
                "approvalSourceType", "plan_step",
                "planStepId", step.stepId(),
                "nextStepNo", step.stepNo() == null ? null : step.stepNo() + 1
        );
    }
}
