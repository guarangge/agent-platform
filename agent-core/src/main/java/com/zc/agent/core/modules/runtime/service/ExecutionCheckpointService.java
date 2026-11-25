package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpointForkResult;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.protocol.service.CheckpointRuntimeEventService;
import com.zc.agent.core.modules.tool.service.ToolSideEffectService;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 持久化执行检查点及进程中断恢复现场服务。 */
@Service
public class ExecutionCheckpointService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final ToolSideEffectService toolSideEffectService;
    private final CheckpointRuntimeEventService checkpointRuntimeEventService;

    public ExecutionCheckpointService(JdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      ConversationRepository conversationRepository,
                                      ToolSideEffectService toolSideEffectService,
                                      CheckpointRuntimeEventService checkpointRuntimeEventService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.conversationRepository = conversationRepository;
        this.toolSideEffectService = toolSideEffectService;
        this.checkpointRuntimeEventService = checkpointRuntimeEventService;
    }

    /** 保存步骤状态及可重放所需的完整计划、决策和前序结果。 */
    @Transactional
    public void save(RuntimeExecutionRequest request,
                     Long taskStepId,
                     Integer currentStepNo,
                     Integer nextStepNo,
                     String status,
                     List<RuntimeStepResultContext> results) {
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        Long checkpointId = jdbc.queryForObject("""
                INSERT INTO task_execution_checkpoints(
                    tenant_id,organization_id,workspace_id,project_id,task_id,task_step_id,sequence_no,
                    checkpoint_status,current_step_no,next_step_no,plan_snapshot,decision_snapshot,
                    step_results_snapshot,trace_id)
                SELECT ?,?,?,?,?,?,COALESCE(MAX(sequence_no),0)+1,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?
                FROM task_execution_checkpoints WHERE task_id=?
                RETURNING id
                """,
                Long.class,
                tenantId, scope == null ? null : scope.organizationId(), scope == null ? null : scope.workspaceId(),
                projectId, request.taskId(), taskStepId, status, currentStepNo, nextStepNo,
                json(request.executionPlan()), json(request.executionDecision()), json(results), request.traceId(), request.taskId());
        checkpointRuntimeEventService.created(new CheckpointRuntimeEventService.CheckpointEventCommand(
                checkpointId, request.taskId(), taskStepId, status, currentStepNo, nextStepNo,
                request.executionPlan().planId(), request.traceId(), null));
    }

    /** 查询任务最新检查点。 */
    public Optional<RuntimeCheckpoint> latest(Long taskId) {
        List<RuntimeCheckpoint> records = jdbc.query("""
                SELECT id,task_id,task_step_id,checkpoint_status,current_step_no,next_step_no,
                       plan_snapshot::text,decision_snapshot::text,step_results_snapshot::text,trace_id,
                       recovery_disposition,ambiguity_reason
                FROM task_execution_checkpoints WHERE task_id=? ORDER BY sequence_no DESC LIMIT 1
                """, (rs, rowNum) -> new RuntimeCheckpoint(
                rs.getLong("id"), rs.getLong("task_id"), (Long) rs.getObject("task_step_id"),
                rs.getString("checkpoint_status"), (Integer) rs.getObject("current_step_no"),
                rs.getInt("next_step_no"), read(rs.getString("plan_snapshot"), ExecutionPlan.class),
                read(rs.getString("decision_snapshot"), ExecutionDecision.class),
                readResults(rs.getString("step_results_snapshot")), rs.getString("trace_id"),
                rs.getString("recovery_disposition"), rs.getString("ambiguity_reason")
        ), taskId);
        return records.stream().findFirst();
    }

    /** 将进程异常中断的现场转成现有 Recovery 主链可识别的失败步骤。 */
    public RuntimeCheckpoint prepareAutomaticRecovery(Long taskId, Long operatorId) {
        RuntimeCheckpoint checkpoint = latest(taskId).orElseThrow(() -> new BusinessException("任务不存在可恢复的持久化 Checkpoint"));
        if ("waiting_approval".equals(checkpoint.status())) {
            throw new BusinessException("任务正在等待审批，不能自动恢复");
        }
        ToolSideEffectService.AmbiguityAssessment assessment = toolSideEffectService.assessRecovery(taskId);
        if (assessment.ambiguous()) {
            jdbc.update("""
                    UPDATE task_execution_checkpoints SET recovery_disposition='manual_review',ambiguity_reason=?
                    WHERE id=?
                    """, assessment.reason(), checkpoint.id());
            throw new BusinessException(assessment.reason());
        }
        int recoveryStepNo = "step_succeeded".equals(checkpoint.status())
                ? checkpoint.nextStepNo() : checkpoint.currentStepNo();
        ExecutionPlan.ExecutionPlanStep planStep = checkpoint.plan().steps().stream()
                .filter(step -> step.stepNo().equals(recoveryStepNo))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Checkpoint 中不存在待恢复步骤 " + recoveryStepNo));
        int updated = jdbc.update("""
                UPDATE task_steps SET status='failed',error_message='进程中断，等待 Checkpoint 自动恢复',
                    finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=?
                WHERE id=? AND status='processing' AND is_deleted=FALSE
                """, operatorId, checkpoint.taskStepId());
        if (updated == 0 && !"step_failed".equals(checkpoint.status())) {
            Long syntheticStepId = conversationRepository.insertTaskStep(taskId, recoveryStepNo, planStep.stepType(),
                    planStep.stepName(), "{\"recovery\":\"durable_checkpoint\"}", operatorId);
            jdbc.update("""
                    UPDATE task_steps SET status='failed',error_message='从持久化 Checkpoint 恢复',
                        finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?
                    """, operatorId, syntheticStepId);
        }
        jdbc.update("UPDATE tasks SET status='failed',finished_at=CURRENT_TIMESTAMP,error_message='等待 Checkpoint 自动恢复',updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?",
                operatorId, taskId);
        return checkpoint;
    }

    /** 标记快照已被实际恢复，并发布 checkpoint.restored。 */
    @Transactional
    public void markRestored(RuntimeCheckpoint checkpoint, String recoveryMode) {
        jdbc.update("UPDATE task_execution_checkpoints SET restored_at=CURRENT_TIMESTAMP WHERE id=?", checkpoint.id());
        checkpointRuntimeEventService.restored(toEventCommand(checkpoint), recoveryMode);
    }

    /** 人工核验外部副作用后解除 Checkpoint 恢复阻断。 */
    public RuntimeCheckpoint resolveAmbiguity(ExecutionContext context,
                                              Long taskId,
                                              ResolveCheckpointAmbiguityRequest request,
                                              Long operatorId) {
        RuntimeCheckpoint checkpoint = latest(taskId).orElseThrow(() -> new BusinessException("任务不存在可恢复的持久化 Checkpoint"));
        toolSideEffectService.resolveRecoveryAmbiguity(context, taskId, request.action(), request.resultSnapshot(),
                request.resolutions(), operatorId);
        String action = request.resolutions() == null || request.resolutions().isEmpty()
                ? request.action()
                : request.resolutions().stream().map(ResolveCheckpointAmbiguityRequest.SideEffectResolution::action)
                    .distinct().reduce((left, right) -> "mixed").orElse("mixed");
        String disposition = switch (action == null ? "" : action) {
            case "confirm_success" -> "confirmed_success";
            case "retry" -> "retry_authorized";
            case "compensate", "confirm_compensated" -> "confirmed_compensated";
            case "mixed" -> "resolved_mixed";
            default -> throw new BusinessException("不支持的恢复处置动作: " + action);
        };
        jdbc.update("""
                UPDATE task_execution_checkpoints SET recovery_disposition=?,ambiguity_reason=NULL,
                    ambiguity_resolved_at=CURRENT_TIMESTAMP,ambiguity_resolved_by=? WHERE id=?
                """, disposition, operatorId, checkpoint.id());
        return latest(taskId).orElse(checkpoint);
    }

    /** 从指定不可变 Checkpoint 创建独立子任务，源任务及其执行历史保持不变。 */
    @Transactional
    public RuntimeCheckpointForkResult fork(Long sourceTaskId, Long checkpointId, Long operatorId) {
        Map<String, Object> source = jdbc.queryForMap("""
                SELECT t.conversation_id,t.message_id,t.agent_id,t.root_task_id,t.prompt_id,t.prompt_name,
                       c.task_step_id,c.current_step_no,c.next_step_no,c.checkpoint_status
                FROM tasks t JOIN task_execution_checkpoints c ON c.task_id=t.id
                WHERE t.id=? AND c.id=? AND t.is_deleted=FALSE
                """, sourceTaskId, checkpointId);
        int resumeStepNo = "step_succeeded".equals(source.get("checkpoint_status"))
                ? ((Number) source.get("next_step_no")).intValue()
                : ((Number) source.get("current_step_no")).intValue();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String taskCode = "TASK_FORK_" + suffix;
        String traceId = "TRACE_FORK_" + suffix;
        String planId = "PLAN_FORK_" + suffix;
        Long rootTaskId = source.get("root_task_id") == null
                ? sourceTaskId : ((Number) source.get("root_task_id")).longValue();
        Long newTaskId = jdbc.queryForObject("""
                INSERT INTO tasks(task_code,trace_id,conversation_id,message_id,agent_id,parent_task_id,root_task_id,
                    trigger_task_step_id,task_kind,prompt_id,prompt_name,status,started_at,error_message,metadata,
                    created_at,created_by,updated_at,updated_by,is_deleted)
                VALUES(?,?,?,?,?,?,?,?, 'checkpoint_fork',?,?, 'failed',CURRENT_TIMESTAMP,'从 Checkpoint 分支恢复',
                    jsonb_build_object('forkedFromTaskId',?,'forkedFromCheckpointId',?),CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?,FALSE)
                RETURNING id
                """, Long.class, taskCode, traceId, source.get("conversation_id"), source.get("message_id"),
                source.get("agent_id"), sourceTaskId, rootTaskId, source.get("task_step_id"), source.get("prompt_id"),
                source.get("prompt_name"), sourceTaskId, checkpointId, operatorId, operatorId);
        Long syntheticStepId = conversationRepository.insertTaskStep(newTaskId, resumeStepNo, "checkpoint_fork",
                "从 Checkpoint 恢复", "{\"sourceCheckpointId\":" + checkpointId + "}", operatorId);
        jdbc.update("""
                UPDATE task_steps SET status='failed',error_message='从指定 Checkpoint 创建分支',
                    finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?
                """, operatorId, syntheticStepId);
        jdbc.update("""
                INSERT INTO task_execution_checkpoints(tenant_id,organization_id,workspace_id,project_id,task_id,
                    task_step_id,sequence_no,checkpoint_status,current_step_no,next_step_no,plan_snapshot,
                    decision_snapshot,step_results_snapshot,trace_id,recovery_disposition,created_at)
                SELECT tenant_id,organization_id,workspace_id,project_id,?,?,1,'step_failed',?,?,
                    jsonb_set(plan_snapshot,'{planId}',to_jsonb(CAST(? AS text))),decision_snapshot,
                    step_results_snapshot,?,'safe_resume',CURRENT_TIMESTAMP
                FROM task_execution_checkpoints WHERE id=? AND task_id=?
                """, newTaskId, syntheticStepId, resumeStepNo, resumeStepNo, planId, traceId, checkpointId, sourceTaskId);
        return new RuntimeCheckpointForkResult(
                newTaskId, taskCode, traceId, sourceTaskId, checkpointId, resumeStepNo);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Checkpoint 序列化失败", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new BusinessException("Checkpoint 快照损坏: " + type.getSimpleName());
        }
    }

    private List<RuntimeStepResultContext> readResults(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new BusinessException("Checkpoint 步骤结果快照损坏");
        }
    }

    private CheckpointRuntimeEventService.CheckpointEventCommand toEventCommand(RuntimeCheckpoint checkpoint) {
        return new CheckpointRuntimeEventService.CheckpointEventCommand(
                checkpoint.id(), checkpoint.taskId(), checkpoint.taskStepId(), checkpoint.status(),
                checkpoint.currentStepNo(), checkpoint.nextStepNo(), checkpoint.plan().planId(),
                checkpoint.traceId(), checkpoint.recoveryDisposition());
    }

}
