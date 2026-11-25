package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** PostgreSQL Interactive Loop Checkpoint 存储实现。 */
@Service
public class JdbcInteractiveLoopCheckpointStore implements InteractiveLoopCheckpointStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcInteractiveLoopCheckpointStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 保存审批前预算、Observation 与待执行 Action。 */
    @Override
    @Transactional
    public void saveWaitingApproval(Long taskId,
                                    String traceId,
                                    InteractiveLoopBudgetState.PersistentSnapshot budgetState,
                                    List<InteractiveLoopObservation> observations,
                                    InteractiveLoopAction pendingAction,
                                    ApprovalRequiredException approval) {
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        Map<String, Object> metadata = approval.approvalMetadata();
        jdbc.update("""
                INSERT INTO interactive_loop_checkpoints(
                    tenant_id,organization_id,workspace_id,project_id,task_id,sequence_no,checkpoint_status,
                    budget_snapshot,observations_snapshot,pending_action_snapshot,approval_node_id,
                    approval_node_name,tool_call_id,parameter_hash,approval_metadata,trace_id)
                SELECT ?,?,?,?,?,COALESCE(MAX(sequence_no),0)+1,'waiting_approval',CAST(? AS jsonb),
                    CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,CAST(? AS jsonb),?
                FROM interactive_loop_checkpoints WHERE task_id=?
                """, tenantId, scope == null ? null : scope.organizationId(),
                scope == null ? null : scope.workspaceId(), projectId, taskId,
                json(budgetState), json(observations), json(pendingAction), approval.approvalNodeId(),
                approval.approvalNodeName(), text(metadata.get("toolCallId")),
                text(metadata.get("parameterHash")), json(metadata), traceId, taskId);
    }

    /** 读取最新待审批现场，历史已恢复快照不会被再次执行。 */
    @Override
    public Optional<InteractiveLoopCheckpoint> latestWaitingApproval(Long taskId) {
        List<InteractiveLoopCheckpoint> records = jdbc.query("""
                SELECT id,task_id,trace_id,budget_snapshot::text,observations_snapshot::text,
                       pending_action_snapshot::text,approval_node_id,approval_node_name,tool_call_id,
                       parameter_hash,approval_metadata::text
                FROM interactive_loop_checkpoints
                WHERE task_id=? AND checkpoint_status='waiting_approval'
                ORDER BY sequence_no DESC LIMIT 1
                """, (rs, rowNum) -> new InteractiveLoopCheckpoint(
                rs.getLong("id"), rs.getLong("task_id"), rs.getString("trace_id"),
                read(rs.getString("budget_snapshot"), InteractiveLoopBudgetState.PersistentSnapshot.class),
                read(rs.getString("observations_snapshot"), new TypeReference<List<InteractiveLoopObservation>>() { }),
                read(rs.getString("pending_action_snapshot"), InteractiveLoopAction.class),
                rs.getString("approval_node_id"), rs.getString("approval_node_name"),
                rs.getString("tool_call_id"), rs.getString("parameter_hash"),
                read(rs.getString("approval_metadata"), new TypeReference<Map<String, Object>>() { })
        ), taskId);
        return records.stream().findFirst();
    }

    /** 仅允许待审批 Checkpoint 单向转换为已恢复。 */
    @Override
    @Transactional
    public void markRestored(Long checkpointId) {
        int updated = jdbc.update("""
                UPDATE interactive_loop_checkpoints
                SET checkpoint_status='restored',restored_at=CURRENT_TIMESTAMP
                WHERE id=? AND checkpoint_status='waiting_approval'
                """, checkpointId);
        if (updated != 1) {
            throw new BusinessException("Interactive Loop Checkpoint 已恢复或不存在");
        }
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Interactive Loop Checkpoint 序列化失败", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new BusinessException("Interactive Loop Checkpoint 快照损坏: " + type.getSimpleName());
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new BusinessException("Interactive Loop Checkpoint JSON 快照损坏");
        }
    }
}
