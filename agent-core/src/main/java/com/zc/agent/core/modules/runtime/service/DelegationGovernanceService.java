package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Delegation 深度、扇出、循环、能力选择、超时与取消传播治理。 */
@Service
public class DelegationGovernanceService {
    private final JdbcTemplate jdbc;

    public DelegationGovernanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 选择目标 Agent 并校验父子任务树治理约束。 */
    public Selection selectAndValidate(ExecutionContext context,
                                       Map<String, Object> inputs,
                                       Long parentTaskId) {
        Policy policy = resolvePolicy(context);
        List<String> capabilities = stringList(inputs == null ? null : inputs.get("requiredCapabilities"));
        Long explicitId = longValue(inputs == null ? null : inputs.get("delegateAgentId"));
        ExecutionContext.DelegateAgent delegate = explicitId == null
                ? selectByCapabilities(context.availableDelegates(), capabilities)
                : context.availableDelegates().stream().filter(item -> explicitId.equals(item.agentId())).findFirst()
                .orElseThrow(() -> new BusinessException("handoff 目标 Agent 不存在或当前不可委派: " + explicitId));
        int depth = jdbc.queryForObject("""
                WITH RECURSIVE ancestors AS (
                    SELECT id,parent_task_id,agent_id FROM tasks WHERE id=? AND is_deleted=FALSE
                    UNION ALL
                    SELECT t.id,t.parent_task_id,t.agent_id FROM tasks t JOIN ancestors a ON t.id=a.parent_task_id
                    WHERE t.is_deleted=FALSE
                ) SELECT COUNT(*) FROM ancestors
                """, Integer.class, parentTaskId);
        if (depth > policy.maxDepth()) {
            throw new BusinessException("Delegation 深度超过 Agent 策略上限 " + policy.maxDepth());
        }
        Integer childCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE parent_task_id=? AND is_deleted=FALSE", Integer.class, parentTaskId);
        if (childCount != null && childCount >= policy.maxChildrenPerTask()) {
            throw new BusinessException("Delegation 子任务数超过 Agent 策略上限 " + policy.maxChildrenPerTask());
        }
        if (!policy.allowAgentReentry()) {
            Integer cycleCount = jdbc.queryForObject("""
                    WITH RECURSIVE ancestors AS (
                        SELECT id,parent_task_id,agent_id FROM tasks WHERE id=? AND is_deleted=FALSE
                        UNION ALL
                        SELECT t.id,t.parent_task_id,t.agent_id FROM tasks t JOIN ancestors a ON t.id=a.parent_task_id
                        WHERE t.is_deleted=FALSE
                    ) SELECT COUNT(*) FROM ancestors WHERE agent_id=?
                    """, Integer.class, parentTaskId, delegate.agentId());
            if (cycleCount != null && cycleCount > 0) {
                throw new BusinessException("Delegation 检测到 Agent 回环: " + delegate.agentCode());
            }
        }
        String reason = explicitId == null
                ? "按 requiredCapabilities 匹配: " + String.join(",", capabilities)
                : "Planner 显式指定 delegateAgentId";
        return new Selection(delegate, capabilities, reason, depth, policy.timeoutSeconds(), policy);
    }

    /** 将治理判定写入已有委派记录。 */
    public void record(Long delegationId, Selection selection) {
        jdbc.update("""
                UPDATE task_delegations SET delegation_depth=?,required_capabilities=CAST(? AS jsonb),
                    selection_reason=?,timeout_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, selection.depth(), jsonArray(selection.requiredCapabilities()), selection.selectionReason(),
                Timestamp.valueOf(LocalDateTime.now().plusSeconds(selection.timeoutSeconds())), delegationId);
    }

    /** 父任务取消时递归标记全部子任务和委派记录。 */
    public void propagateCancellation(Long rootTaskId, Long operatorId) {
        jdbc.update("""
                WITH RECURSIVE descendants AS (
                    SELECT id FROM tasks WHERE parent_task_id=? AND is_deleted=FALSE
                    UNION ALL SELECT t.id FROM tasks t JOIN descendants d ON t.parent_task_id=d.id WHERE t.is_deleted=FALSE
                ) UPDATE tasks SET status='cancelled',finished_at=CURRENT_TIMESTAMP,error_message='父任务取消',
                    updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id IN (SELECT id FROM descendants)
                    AND status NOT IN ('success','failed','cancelled')
                """, rootTaskId, operatorId);
        jdbc.update("""
                WITH RECURSIVE descendants AS (
                    SELECT id FROM tasks WHERE parent_task_id=? AND is_deleted=FALSE
                    UNION ALL SELECT t.id FROM tasks t JOIN descendants d ON t.parent_task_id=d.id WHERE t.is_deleted=FALSE
                ) UPDATE task_delegations SET status='cancelled',cancellation_requested_at=CURRENT_TIMESTAMP,
                    error_message='父任务取消',updated_at=CURRENT_TIMESTAMP,updated_by=?
                WHERE child_task_id IN (SELECT id FROM descendants) AND status='processing'
                """, rootTaskId, operatorId);
    }

    private Policy resolvePolicy(ExecutionContext context) {
        JsonNode node = context.agentContext().metadata() == null
                ? null : context.agentContext().metadata().path("delegationPolicy");
        return new Policy(bounded(node, "maxDepth", 3, 1, 10),
                bounded(node, "maxChildrenPerTask", 5, 1, 50),
                bounded(node, "timeoutSeconds", 120, 5, 3600),
                node != null && node.path("allowAgentReentry").asBoolean(false));
    }

    private ExecutionContext.DelegateAgent selectByCapabilities(List<ExecutionContext.DelegateAgent> delegates,
                                                                 List<String> capabilities) {
        if (capabilities.isEmpty()) {
            throw new BusinessException("handoff 未指定 delegateAgentId 时必须提供 requiredCapabilities");
        }
        return delegates.stream()
                .map(delegate -> Map.entry(delegate, capabilityScore(delegate, capabilities)))
                .filter(entry -> entry.getValue() == capabilities.size())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new BusinessException("没有 Agent 匹配 requiredCapabilities: " + capabilities));
    }

    private int capabilityScore(ExecutionContext.DelegateAgent delegate, List<String> capabilities) {
        String text = (delegate.agentCode() + " " + delegate.agentName() + " " + delegate.description()).toLowerCase(Locale.ROOT);
        return (int) capabilities.stream().filter(item -> text.contains(item.toLowerCase(Locale.ROOT))).count();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            List<String> values = new ArrayList<>();
            for (String item : text.split("[,，]")) {
                if (!item.isBlank()) values.add(item.trim());
            }
            return values;
        }
        return List.of();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException("delegateAgentId 不是合法数字");
        }
    }

    private int bounded(JsonNode node, String field, int defaultValue, int min, int max) {
        int value = node == null ? defaultValue : node.path(field).asInt(defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private String jsonArray(List<String> values) {
        return "[" + values.stream().map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    public record Policy(int maxDepth, int maxChildrenPerTask, int timeoutSeconds, boolean allowAgentReentry) {
    }

    public record Selection(ExecutionContext.DelegateAgent delegate,
                            List<String> requiredCapabilities,
                            String selectionReason,
                            int depth,
                            int timeoutSeconds,
                            Policy policy) {
    }
}
