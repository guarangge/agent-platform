package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.security.TenantScopeContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 用户长期记忆来源、置信度、TTL、替代和撤销治理服务。 */
@Service
public class MemoryGovernanceService {
    private final JdbcTemplate jdbc;

    public MemoryGovernanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 将本轮四类汇总作为新记忆版本写入，并撤销同类型旧版本。 */
    @Transactional
    public void persist(ExecutionContext context,
                        Long taskId,
                        Map<String, String> values,
                        Long operatorId) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            persistOne(context, taskId, entry.getKey(), entry.getValue().trim(), operatorId);
        }
    }

    /** 查询当前用户与 Agent 范围内未撤销且未过期的最新记忆。 */
    public List<MemoryItem> findActive(Long userId, Long agentId) {
        return jdbc.query("""
                SELECT id,memory_type,content,confidence,source_task_id,source_message_id,expires_at,created_at
                FROM memory_governance_items
                WHERE user_id=? AND agent_id=? AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)
                ORDER BY memory_type,updated_at DESC
                """, (rs, rowNum) -> new MemoryItem(rs.getLong("id"), rs.getString("memory_type"),
                rs.getString("content"), rs.getBigDecimal("confidence"), (Long) rs.getObject("source_task_id"),
                (Long) rs.getObject("source_message_id"), nullableTime(rs.getTimestamp("expires_at")),
                rs.getTimestamp("created_at").toLocalDateTime()), userId, agentId);
    }

    /** 判断该用户与 Agent 是否已启用过独立记忆治理，撤销后仍返回 true。 */
    public boolean hasHistory(Long userId, Long agentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM memory_governance_items WHERE user_id=? AND agent_id=?", Integer.class,
                userId, agentId);
        return count != null && count > 0;
    }

    /** 用户撤销自己的记忆项。 */
    public boolean revoke(Long memoryId, Long userId, String reason) {
        return jdbc.update("""
                UPDATE memory_governance_items SET revoked_at=CURRENT_TIMESTAMP,revoke_reason=?,updated_at=CURRENT_TIMESTAMP,updated_by=?
                WHERE id=? AND user_id=? AND revoked_at IS NULL
                """, reason == null || reason.isBlank() ? "用户撤销" : reason.trim(), userId, memoryId, userId) > 0;
    }

    private void persistOne(ExecutionContext context,
                            Long taskId,
                            String memoryType,
                            String content,
                            Long operatorId) {
        TenantScopeContext.Scope scope = TenantScopeContext.current();
        long tenantId = scope == null || scope.tenantId() == null ? 1L : scope.tenantId();
        long projectId = scope == null || scope.projectId() == null ? 1L : scope.projectId();
        Long previousId = jdbc.query("""
                SELECT id FROM memory_governance_items
                WHERE user_id=? AND agent_id=? AND memory_type=? AND revoked_at IS NULL
                ORDER BY updated_at DESC,id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null,
                context.currentUser().userId(), context.agentContext().agentId(), memoryType);
        if (previousId != null) {
            String previousHash = jdbc.queryForObject(
                    "SELECT content_hash FROM memory_governance_items WHERE id=?", String.class, previousId);
            if (hash(content).equals(previousHash)) {
                jdbc.update("UPDATE memory_governance_items SET confidence=?,expires_at=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?",
                        confidence(memoryType), expiresAt(context, memoryType), operatorId, previousId);
                return;
            }
        }
        Long newId = jdbc.queryForObject("""
                INSERT INTO memory_governance_items(
                    tenant_id,organization_id,workspace_id,project_id,user_id,agent_id,conversation_id,
                    source_task_id,source_message_id,memory_type,content,content_hash,confidence,
                    supersedes_memory_id,expires_at,metadata,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?) RETURNING id
                """, Long.class, tenantId, scope == null ? null : scope.organizationId(),
                scope == null ? null : scope.workspaceId(), projectId, context.currentUser().userId(),
                context.agentContext().agentId(), context.conversationContext().conversationId(), taskId,
                context.messageContext().userMessageId(), memoryType, content, hash(content), confidence(memoryType),
                previousId, expiresAt(context, memoryType), "{\"extractor\":\"memory_update_pipeline\"}", operatorId, operatorId);
        if (previousId != null) {
            jdbc.update("""
                    UPDATE memory_governance_items SET revoked_at=CURRENT_TIMESTAMP,revoke_reason='superseded',
                        superseded_by_memory_id=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?
                    """, newId, operatorId, previousId);
        }
    }

    private Timestamp expiresAt(ExecutionContext context, String memoryType) {
        JsonNode policy = context.agentContext().metadata() == null
                ? null : context.agentContext().metadata().path("memoryPolicy");
        int defaultDays = "attention".equals(memoryType) ? 30 : 180;
        int ttlDays = policy == null ? defaultDays : policy.path(memoryType + "TtlDays").asInt(defaultDays);
        return ttlDays <= 0 ? null : Timestamp.valueOf(LocalDateTime.now().plusDays(Math.min(ttlDays, 3650)));
    }

    private java.math.BigDecimal confidence(String memoryType) {
        return new java.math.BigDecimal("preference".equals(memoryType) ? "0.8000" : "0.7500");
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalDateTime nullableTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record MemoryItem(Long memoryId,
                             String memoryType,
                             String content,
                             java.math.BigDecimal confidence,
                             Long sourceTaskId,
                             Long sourceMessageId,
                             LocalDateTime expiresAt,
                             LocalDateTime createdAt) {
    }
}
