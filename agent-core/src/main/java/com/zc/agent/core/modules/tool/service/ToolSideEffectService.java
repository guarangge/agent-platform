package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.runtime.capability.model.ToolSideEffectLevel;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tool 副作用幂等占用、结果复用、失败不确定性和逆序补偿服务。 */
@Service
public class ToolSideEffectService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ToolRouterService toolRouterService;

    public ToolSideEffectService(JdbcTemplate jdbc, ObjectMapper mapper, @Lazy ToolRouterService toolRouterService) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.toolRouterService = toolRouterService;
    }

    /** 在 Provider 调用前占用幂等键，重复成功调用直接返回历史结果。 */
    public Lease begin(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        Policy policy = policy(tool);
        if (policy == null) {
            return Lease.none();
        }
        String requestHash = hash(request.toolInputText() + "|" + json(request.arguments()));
        String customKey = request.arguments() == null ? null : text(request.arguments().get("_idempotencyKey"));
        if ("required_custom".equals(policy.idempotencyMode()) && customKey == null) {
            throw error("副作用 Tool 要求调用方提供 _idempotencyKey", "TOOL_IDEMPOTENCY_KEY_REQUIRED");
        }
        if (customKey != null && customKey.length() > 160) {
            throw error("_idempotencyKey 长度不能超过 160", "TOOL_IDEMPOTENCY_KEY_INVALID");
        }
        String key = customKey == null
                ? "auto:" + hash(context.taskContext().taskId() + "|" + tool.toolCode() + "|" + requestHash)
                : customKey;
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        Long id = jdbc.query("""
                INSERT INTO tool_side_effect_executions(tenant_id,organization_id,workspace_id,project_id,task_id,tool_code,idempotency_key,request_hash,side_effect_level,compensation_tool_code,status)
                VALUES(?,?,?,?,?,?,?,?,?,?, 'processing') ON CONFLICT DO NOTHING RETURNING id
                """, rs -> rs.next() ? rs.getLong(1) : null, tenantId,
                scope == null ? null : scope.organizationId(), scope == null ? null : scope.workspaceId(), projectId,
                context.taskContext().taskId(), tool.toolCode(), key, requestHash, policy.level(), policy.compensationToolCode());
        if (id != null) {
            return new Lease(id, false, null);
        }
        Map<String,Object> existing = jdbc.queryForMap("""
                SELECT id,status,request_hash,result_snapshot::text FROM tool_side_effect_executions
                WHERE tenant_id=? AND project_id=? AND tool_code=? AND idempotency_key=?
                """, tenantId, projectId, tool.toolCode(), key);
        if (!requestHash.equals(existing.get("request_hash"))) {
            throw error("相同幂等键对应不同 Tool 输入", "TOOL_IDEMPOTENCY_CONFLICT");
        }
        if ("success".equals(existing.get("status"))) {
            return new Lease(((Number) existing.get("id")).longValue(), true,
                    parseResult(String.valueOf(existing.get("result_snapshot"))));
        }
        if (List.of("retry_authorized", "compensated").contains(String.valueOf(existing.get("status")))) {
            int claimed = jdbc.update("""
                    UPDATE tool_side_effect_executions SET status='processing',error_message=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND status IN ('retry_authorized','compensated')
                    """, ((Number) existing.get("id")).longValue());
            if (claimed == 1) {
                return new Lease(((Number) existing.get("id")).longValue(), false, null);
            }
        }
        throw error("副作用 Tool 的历史调用状态为 " + existing.get("status") + "，禁止自动重复执行", "TOOL_SIDE_EFFECT_UNCERTAIN");
    }

    /** 查询任务中会阻断 Checkpoint 自动重放的副作用执行。 */
    public AmbiguityAssessment assessRecovery(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,tool_code,side_effect_level,status,error_message
                FROM tool_side_effect_executions
                WHERE task_id=? AND status IN ('processing','uncertain') ORDER BY id DESC
                """, taskId);
        if (rows.isEmpty()) {
            return new AmbiguityAssessment(false, "safe_resume", "", List.of());
        }
        boolean irreversible = rows.stream().anyMatch(row -> "irreversible".equals(row.get("side_effect_level")));
        String reason = rows.stream()
                .map(row -> row.get("tool_code") + "[" + row.get("side_effect_level") + "/" + row.get("status") + "]")
                .reduce((left, right) -> left + ", " + right)
                .orElse("存在不确定副作用");
        return new AmbiguityAssessment(true, "manual_review",
                (irreversible ? "存在不可逆 Tool，" : "存在副作用 Tool，") + "禁止自动重放：" + reason,
                rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList());
    }

    /** 按每次 Tool execution 的独立处置解除副作用不确定状态。 */
    public void resolveRecoveryAmbiguity(ExecutionContext context,
                                         Long taskId,
                                         String legacyAction,
                                         Map<String, Object> legacyResultSnapshot,
                                         List<ResolveCheckpointAmbiguityRequest.SideEffectResolution> requestedResolutions,
                                         Long operatorId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,tool_code,side_effect_level,status,compensation_tool_code,error_message
                FROM tool_side_effect_executions
                WHERE task_id=? AND status IN ('processing','uncertain') ORDER BY id
                """, taskId);
        if (rows.isEmpty()) {
            throw error("任务不存在待处理的副作用不确定记录", "TOOL_SIDE_EFFECT_AMBIGUITY_NOT_FOUND");
        }
        List<ResolveCheckpointAmbiguityRequest.SideEffectResolution> resolutions = normalizeResolutions(
                rows, legacyAction, legacyResultSnapshot, requestedResolutions);
        Map<Long, Map<String, Object>> rowById = rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(), row -> row));
        Set<Long> expected = rowById.keySet();
        Set<Long> actual = resolutions.stream().map(ResolveCheckpointAmbiguityRequest.SideEffectResolution::executionId)
                .collect(Collectors.toSet());
        if (resolutions.size() != actual.size() || !expected.equals(actual)) {
            throw error("必须为每条不确定副作用记录提供且仅提供一次处置", "TOOL_SIDE_EFFECT_RESOLUTION_INCOMPLETE");
        }
        for (ResolveCheckpointAmbiguityRequest.SideEffectResolution resolution : resolutions) {
            resolveOne(context, taskId, rowById.get(resolution.executionId()), resolution, operatorId);
        }
    }

    private List<ResolveCheckpointAmbiguityRequest.SideEffectResolution> normalizeResolutions(
            List<Map<String, Object>> rows,
            String legacyAction,
            Map<String, Object> legacyResultSnapshot,
            List<ResolveCheckpointAmbiguityRequest.SideEffectResolution> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        if (legacyAction == null || legacyAction.isBlank()) {
            throw error("缺少副作用处置动作", "TOOL_SIDE_EFFECT_RESOLUTION_INVALID");
        }
        if (rows.size() > 1 && "confirm_success".equals(legacyAction)) {
            throw error("多条副作用记录不能共用 resultSnapshot，请使用 resolutions 逐条确认", "TOOL_SIDE_EFFECT_RESOLUTION_INCOMPLETE");
        }
        return rows.stream().map(row -> new ResolveCheckpointAmbiguityRequest.SideEffectResolution(
                ((Number) row.get("id")).longValue(), legacyAction, legacyResultSnapshot)).toList();
    }

    private void resolveOne(ExecutionContext context,
                            Long taskId,
                            Map<String, Object> row,
                            ResolveCheckpointAmbiguityRequest.SideEffectResolution resolution,
                            Long operatorId) {
        Long executionId = ((Number) row.get("id")).longValue();
        String previousStatus = text(row.get("status"));
        switch (resolution.action()) {
            case "confirm_success" -> {
                if (resolution.resultSnapshot() == null || resolution.resultSnapshot().isEmpty()) {
                    throw error("确认成功必须提供 resultSnapshot", "TOOL_SIDE_EFFECT_RESULT_REQUIRED");
                }
                jdbc.update("""
                        UPDATE tool_side_effect_executions SET status='success',result_snapshot=CAST(? AS jsonb),
                            completed_at=CURRENT_TIMESTAMP,error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                        """, json(resolution.resultSnapshot()), executionId);
                audit(taskId, executionId, resolution.action(), previousStatus, "success",
                        resolution.resultSnapshot(), null, operatorId);
            }
            case "retry" -> {
                if ("irreversible".equals(row.get("side_effect_level"))) {
                    throw error("不可逆 Tool 不允许直接授权重试", "TOOL_SIDE_EFFECT_RETRY_FORBIDDEN");
                }
                jdbc.update("UPDATE tool_side_effect_executions SET status='retry_authorized',updated_at=CURRENT_TIMESTAMP WHERE id=?", executionId);
                audit(taskId, executionId, resolution.action(), previousStatus, "retry_authorized", Map.of(), null, operatorId);
            }
            case "compensate" -> executeResolutionCompensation(context, taskId, row, previousStatus, operatorId);
            case "confirm_compensated" -> {
                if (!"compensatable".equals(row.get("side_effect_level"))) {
                    throw error("只有 compensatable Tool 可以确认已补偿", "TOOL_SIDE_EFFECT_COMPENSATION_FORBIDDEN");
                }
                jdbc.update("UPDATE tool_side_effect_executions SET status='compensated',compensated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", executionId);
                audit(taskId, executionId, resolution.action(), previousStatus, "compensated",
                        resolution.resultSnapshot(), null, operatorId);
            }
            default -> throw error("不支持的处置动作: " + resolution.action(), "TOOL_SIDE_EFFECT_RESOLUTION_INVALID");
        }
    }

    private void executeResolutionCompensation(ExecutionContext context,
                                               Long taskId,
                                               Map<String, Object> row,
                                               String previousStatus,
                                               Long operatorId) {
        if (!"compensatable".equals(row.get("side_effect_level"))) {
            throw error("只有 compensatable Tool 可以执行补偿", "TOOL_SIDE_EFFECT_COMPENSATION_FORBIDDEN");
        }
        String compensationCode = text(row.get("compensation_tool_code"));
        if (compensationCode == null) {
            throw error("副作用记录缺少 compensationToolCode", "TOOL_COMPENSATION_MISSING");
        }
        Long executionId = ((Number) row.get("id")).longValue();
        jdbc.update("UPDATE tool_side_effect_executions SET status='compensating',updated_at=CURRENT_TIMESTAMP WHERE id=?", executionId);
        try {
            ToolExecutionResult result = toolRouterService.execute(context, compensationCode, "恢复阶段补偿 Tool 副作用", Map.of(
                    "_compensationEffectId", executionId,
                    "originalToolCode", row.get("tool_code"),
                    "uncertainError", text(row.get("error_message")) == null ? "" : text(row.get("error_message"))
            )).orElseThrow(() -> error("补偿 Tool 不可用: " + compensationCode, "COMPENSATION_TOOL_UNAVAILABLE"));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("toolCode", result.toolCode());
            snapshot.put("summary", result.summary());
            snapshot.put("outputSnapshot", result.outputSnapshot());
            jdbc.update("UPDATE tool_side_effect_executions SET status='compensated',compensated_at=CURRENT_TIMESTAMP,error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", executionId);
            audit(taskId, executionId, "compensate", previousStatus, "compensated", snapshot, null, operatorId);
        } catch (Exception exception) {
            jdbc.update("UPDATE tool_side_effect_executions SET status='compensation_failed',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", exception.getMessage(), executionId);
            audit(taskId, executionId, "compensate", previousStatus, null, Map.of(), exception.getMessage(), operatorId);
            throw error("Tool 补偿失败: " + compensationCode + "，" + exception.getMessage(), "TOOL_COMPENSATION_FAILED");
        }
    }

    private void audit(Long taskId, Long executionId, String action, String previousStatus, String resolvedStatus,
                       Map<String, Object> snapshot, String errorMessage, Long operatorId) {
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        jdbc.update("""
                INSERT INTO tool_side_effect_resolutions(tenant_id,project_id,task_id,side_effect_execution_id,action,
                    previous_status,resolved_status,resolution_snapshot,error_message,resolved_by)
                VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),?,?)
                """, tenantId, projectId, taskId, executionId, action, previousStatus, resolvedStatus,
                json(snapshot == null ? Map.of() : snapshot), errorMessage, operatorId);
    }

    public void success(Lease lease, ToolExecutionResult result) {
        if (!lease.tracked() || lease.cached()) {
            return;
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("toolCode", result.toolCode());
        snapshot.put("toolName", result.toolName());
        snapshot.put("summary", result.summary());
        snapshot.put("responseText", result.responseText());
        snapshot.put("outputSnapshot", result.outputSnapshot());
        jdbc.update("UPDATE tool_side_effect_executions SET status='success',result_snapshot=CAST(? AS jsonb),completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                json(snapshot), lease.id());
    }

    public void uncertain(Lease lease, Exception error) {
        if (!lease.tracked() || lease.cached()) {
            return;
        }
        jdbc.update("UPDATE tool_side_effect_executions SET status='uncertain',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", error.getMessage(), lease.id());
    }

    /** 任务最终失败时按副作用发生逆序调用补偿 Tool。 */
    public void compensateTask(ExecutionContext context) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,tool_code,compensation_tool_code,result_snapshot::text FROM tool_side_effect_executions
                WHERE task_id=? AND status='success' AND side_effect_level='compensatable' ORDER BY id DESC
                """, context.taskContext().taskId());
        for (Map<String, Object> row : rows) {
            String compensationCode = text(row.get("compensation_tool_code"));
            if (compensationCode == null) {
                continue;
            }
            Long id = ((Number) row.get("id")).longValue();
            jdbc.update("UPDATE tool_side_effect_executions SET status='compensating',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='success'", id);
            try {
                toolRouterService.execute(context, compensationCode, "补偿 Tool 副作用", Map.of(
                        "_compensationEffectId", id,
                        "originalToolCode", row.get("tool_code"),
                        "originalResult", row.get("result_snapshot")
                )).orElseThrow(() -> error("补偿 Tool 不可用: " + compensationCode, "COMPENSATION_TOOL_UNAVAILABLE"));
                jdbc.update("UPDATE tool_side_effect_executions SET status='compensated',compensated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
            } catch (Exception exception) {
                jdbc.update("UPDATE tool_side_effect_executions SET status='compensation_failed',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", exception.getMessage(), id);
                throw error("Tool 补偿失败: " + compensationCode + "，" + exception.getMessage(), "TOOL_COMPENSATION_FAILED");
            }
        }
    }

    private Policy policy(RegistryAgentTool tool) {
        JsonNode node = tool.metadata() == null ? null : tool.metadata().path("sideEffectPolicy");
        String declaredLevel = tool.sideEffectLevel();
        // 兼容升级前已经持久化的执行快照；新快照始终使用顶层权威声明。
        String level = declaredLevel == null || declaredLevel.isBlank()
                ? node == null ? "none" : node.path("level").asText("none")
                : declaredLevel;
        try {
            level = ToolSideEffectLevel.require(level);
        } catch (IllegalArgumentException exception) {
            throw error("Tool sideEffectLevel 不合法", "TOOL_SIDE_EFFECT_POLICY_INVALID");
        }
        if ("none".equals(level)) {
            return null;
        }
        String idempotencyMode = node == null ? "generated" : node.path("idempotencyMode").asText("generated");
        if (!List.of("generated", "required_custom").contains(idempotencyMode)) {
            throw error("Tool sideEffectPolicy.idempotencyMode 不合法", "TOOL_SIDE_EFFECT_POLICY_INVALID");
        }
        String compensation = node == null ? null : node.path("compensationToolCode").asText(null);
        if ("compensatable".equals(level) && (compensation == null || compensation.isBlank())) {
            throw error("可补偿 Tool 未配置 compensationToolCode", "TOOL_COMPENSATION_MISSING");
        }
        return new Policy(level, idempotencyMode, compensation);
    }

    private ToolExecutionResult parseResult(String json) {
        try {
            Map<String, Object> value = mapper.readValue(json, new TypeReference<>() {
            });
            return new ToolExecutionResult(text(value.get("toolCode")), text(value.get("toolName")),
                    text(value.get("summary")), text(value.get("responseText")), text(value.get("outputSnapshot")));
        } catch (Exception exception) {
            throw error("幂等结果快照损坏", "TOOL_IDEMPOTENCY_SNAPSHOT_INVALID");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private ToolExecutionException error(String message, String code) {
        return new ToolExecutionException(message, code, "side_effect", false, null, Map.of());
    }

    private record Policy(String level, String idempotencyMode, String compensationToolCode) {
    }

    public record Lease(Long id, boolean cached, ToolExecutionResult cachedResult) {
        static Lease none() {
            return new Lease(null, false, null);
        }

        boolean tracked() {
            return id != null;
        }
    }

    public record AmbiguityAssessment(boolean ambiguous,
                                      String disposition,
                                      String reason,
                                      List<Long> sideEffectExecutionIds) {
    }
}
