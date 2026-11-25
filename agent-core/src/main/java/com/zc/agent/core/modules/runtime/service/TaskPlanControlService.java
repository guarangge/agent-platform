package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 人工计划控制请求、影响预览与步骤边界决策服务。 */
@Service
public class TaskPlanControlService {
    private final JdbcTemplate jdbc;

    public TaskPlanControlService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> request(Long taskId,
                                       String action,
                                       Integer targetStepNo,
                                       String comment,
                                       Long operatorId) {
        if (!List.of("pause", "resume", "skip_step", "single_step").contains(action)) {
            throw new BusinessException("不支持的计划控制动作: " + action);
        }
        if (List.of("skip_step", "single_step").contains(action) && (targetStepNo == null || targetStepNo < 1)) {
            throw new BusinessException(action + " 必须指定 targetStepNo");
        }
        jdbc.update("UPDATE task_plan_controls SET status='cancelled',consumed_at=CURRENT_TIMESTAMP WHERE task_id=? AND status='pending'", taskId);
        if ("resume".equals(action)) {
            int resumed = jdbc.update("""
                    UPDATE tasks SET status='failed',error_message='人工恢复执行',finished_at=CURRENT_TIMESTAMP,
                        updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND status='paused' AND is_deleted=FALSE
                    """, operatorId, taskId);
            if (resumed == 0) {
                throw new BusinessException("当前任务不处于暂停状态，不能恢复");
            }
            jdbc.update("""
                    UPDATE task_steps SET status='failed',error_message='人工恢复执行',finished_at=CURRENT_TIMESTAMP,
                        updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE task_id=? AND status='paused' AND is_deleted=FALSE
                    """, operatorId, taskId);
            int queued = jdbc.update("""
                    UPDATE agent_runs SET status='queued',available_at=CURRENT_TIMESTAMP,worker_id=NULL,lease_until=NULL,
                        finished_at=NULL,error_message=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE task_id=? AND status='paused' AND is_deleted=FALSE
                    """, taskId);
            return Map.of("taskId", taskId, "action", action, "status", queued > 0 ? "queued" : "recovery_ready",
                    "async", queued > 0);
        }
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        Long id = jdbc.queryForObject("""
                INSERT INTO task_plan_controls(tenant_id,project_id,task_id,action,target_step_no,comment,requested_by)
                VALUES(?,?,?,?,?,?,?) RETURNING id
                """, Long.class, tenantId, projectId, taskId, action, targetStepNo, comment, operatorId);
        return Map.of("controlId", id, "taskId", taskId, "action", action,
                "targetStepNo", targetStepNo == null ? 0 : targetStepNo, "status", "pending");
    }

    public Map<String, Object> preview(ExecutionPlan plan, String action, Integer targetStepNo) {
        List<ExecutionPlan.ExecutionPlanStep> impacted = plan.steps().stream()
                .filter(step -> targetStepNo == null || step.stepNo() >= targetStepNo)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("targetStepNo", targetStepNo);
        result.put("impactedSteps", impacted.stream().map(step -> Map.of(
                "stepNo", step.stepNo(), "stepId", step.stepId(), "stepName", step.stepName(),
                "stepType", step.stepType(), "dependsOnStepIds", step.dependsOnStepIds())).toList());
        result.put("warning", "skip_step".equals(action)
                ? "跳过步骤可能导致依赖其结构化输出的后续步骤契约校验失败" : "控制将在安全步骤边界生效");
        return result;
    }

    /** 执行器在每个步骤开始前消费控制请求。 */
    public BoundaryDecision beforeStep(Long taskId, Integer stepNo) {
        List<Map<String, Object>> controls = jdbc.queryForList("""
                SELECT id,action,target_step_no FROM task_plan_controls
                WHERE task_id=? AND status='pending' ORDER BY id DESC LIMIT 1
                """, taskId);
        if (controls.isEmpty()) {
            return BoundaryDecision.proceed();
        }
        Map<String, Object> control = controls.get(0);
        Long id = ((Number) control.get("id")).longValue();
        String action = String.valueOf(control.get("action"));
        Integer target = control.get("target_step_no") == null ? null : ((Number) control.get("target_step_no")).intValue();
        if ("skip_step".equals(action) && stepNo.equals(target)) {
            consume(id);
            return new BoundaryDecision(true, false, "人工跳过步骤");
        }
        if ("single_step".equals(action) && target != null && stepNo < target) {
            throw new PlanControlPauseException("单步目标必须是当前待执行步骤，当前步骤 " + stepNo + "，目标步骤 " + target);
        }
        if ("single_step".equals(action) && target != null && stepNo > target) {
            consume(id);
            throw new PlanControlPauseException("单步执行已完成，在步骤 " + stepNo + " 前暂停");
        }
        if ("pause".equals(action)) {
            consume(id);
            throw new PlanControlPauseException("人工请求在步骤 " + stepNo + " 前暂停");
        }
        return BoundaryDecision.proceed();
    }

    private void consume(Long id) {
        jdbc.update("UPDATE task_plan_controls SET status='consumed',consumed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", id);
    }

    public record BoundaryDecision(boolean skip, boolean pause, String reason) {
        static BoundaryDecision proceed() {
            return new BoundaryDecision(false, false, "");
        }
    }
}
