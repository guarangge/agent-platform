package com.zc.agent.registry.modules.tool.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool Capability Pack 持久化访问。
 */
@Repository
public class ToolCapabilityPackRepository {

    private final JdbcTemplate jdbcTemplate;

    public ToolCapabilityPackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findPacks() {
        return jdbcTemplate.queryForList("""
                SELECT p.id, p.pack_code, p.pack_name, p.description, p.version, p.status,
                       p.metadata::text AS metadata, p.created_at, p.updated_at,
                       COUNT(DISTINCT i.id) FILTER (WHERE i.is_deleted = FALSE) AS tool_count,
                       COUNT(DISTINCT r.id) FILTER (WHERE r.is_deleted = FALSE) AS agent_count
                FROM tool_capability_packs p
                LEFT JOIN tool_capability_pack_items i ON i.pack_id = p.id AND i.is_deleted = FALSE
                LEFT JOIN agent_tool_capability_pack_rel r ON r.pack_id = p.id AND r.is_deleted = FALSE
                WHERE p.is_deleted = FALSE
                GROUP BY p.id ORDER BY p.updated_at DESC, p.id DESC
                """);
    }

    public Optional<Map<String, Object>> findPack(Long packId) {
        return jdbcTemplate.queryForList("""
                SELECT id, pack_code, pack_name, description, version, status, metadata::text AS metadata,
                       created_at, updated_at, created_by, updated_by
                FROM tool_capability_packs WHERE id = ? AND is_deleted = FALSE
                """, packId).stream().findFirst();
    }

    public List<Map<String, Object>> findPackTools(Long packId) {
        return jdbcTemplate.queryForList("""
                SELECT i.tool_id, t.tool_code, t.tool_name, t.tool_type, t.tool_category,
                       t.permission_level, t.status, i.required, i.sort_order, i.metadata::text AS metadata
                FROM tool_capability_pack_items i
                JOIN tools t ON t.id = i.tool_id AND t.is_deleted = FALSE
                WHERE i.pack_id = ? AND i.is_deleted = FALSE
                ORDER BY i.sort_order, i.id
                """, packId);
    }

    public Long insertPack(String code, String name, String description, String version, String status,
                           String metadata, Long operatorId) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tool_capability_packs
                        (pack_code, pack_name, description, version, status, metadata, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setString(4, version);
            statement.setString(5, status);
            statement.setString(6, metadata);
            statement.setObject(7, operatorId);
            statement.setObject(8, operatorId);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    public int updatePack(Long packId, String name, String description, String version, String status,
                          String metadata, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE tool_capability_packs SET pack_name = ?, description = ?, version = ?, status = ?,
                    metadata = CAST(? AS jsonb), updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = FALSE
                """, name, description, version, status, metadata, operatorId, packId);
    }

    public void replacePackTools(Long packId, List<Long> toolIds, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE tool_capability_pack_items SET is_deleted = TRUE, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE pack_id = ? AND is_deleted = FALSE
                """, operatorId, packId);
        for (int index = 0; index < toolIds.size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO tool_capability_pack_items
                        (pack_id, tool_id, required, sort_order, metadata, created_by, updated_by)
                    VALUES (?, ?, TRUE, ?, '{}'::jsonb, ?, ?)
                    """, packId, toolIds.get(index), (index + 1) * 10, operatorId, operatorId);
        }
    }

    public List<Long> findMissingToolIds(List<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(toolIds.size(), "?"));
        List<Long> existing = jdbcTemplate.query("SELECT id FROM tools WHERE is_deleted = FALSE AND id IN ("
                        + placeholders + ")", (rs, rowNum) -> rs.getLong(1), toolIds.toArray());
        return toolIds.stream().filter(id -> !existing.contains(id)).toList();
    }

    public int softDeletePack(Long packId, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE tool_capability_packs SET is_deleted = TRUE, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = FALSE
                  AND NOT EXISTS (SELECT 1 FROM agent_tool_capability_pack_rel r
                                  WHERE r.pack_id = tool_capability_packs.id AND r.is_deleted = FALSE)
                """, operatorId, packId);
    }

    public List<Long> findAgentPackIds(Long agentId) {
        return jdbcTemplate.query("""
                SELECT pack_id FROM agent_tool_capability_pack_rel
                WHERE agent_id = ? AND is_deleted = FALSE ORDER BY pack_id
                """, (rs, rowNum) -> rs.getLong(1), agentId);
    }

    public void replaceAgentPacks(Long agentId, List<Long> packIds, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE agent_tool_capability_pack_rel SET is_deleted = TRUE, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE agent_id = ? AND is_deleted = FALSE
                """, operatorId, agentId);
        packIds.forEach(packId -> jdbcTemplate.update("""
                INSERT INTO agent_tool_capability_pack_rel (agent_id, pack_id, created_by, updated_by)
                VALUES (?, ?, ?, ?)
                """, agentId, packId, operatorId, operatorId));
    }

    public List<Long> findMissingEnabledPackIds(List<Long> packIds) {
        if (packIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(packIds.size(), "?"));
        List<Long> existing = jdbcTemplate.query("""
                SELECT id FROM tool_capability_packs
                WHERE is_deleted = FALSE AND status = 'enabled' AND id IN (""" + placeholders + ")",
                (rs, rowNum) -> rs.getLong(1), packIds.toArray());
        return packIds.stream().filter(id -> !existing.contains(id)).toList();
    }

    public List<Long> findEffectiveToolIdsByAgent(Long agentId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT i.tool_id
                FROM agent_tool_capability_pack_rel r
                JOIN tool_capability_packs p ON p.id = r.pack_id AND p.is_deleted = FALSE AND p.status = 'enabled'
                JOIN tool_capability_pack_items i ON i.pack_id = p.id AND i.is_deleted = FALSE
                JOIN tools t ON t.id = i.tool_id AND t.is_deleted = FALSE AND t.status = 'enabled'
                WHERE r.agent_id = ? AND r.is_deleted = FALSE
                ORDER BY i.tool_id
                """, (rs, rowNum) -> rs.getLong(1), agentId);
    }

    public boolean agentExists(Long agentId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM agents WHERE id = ? AND is_deleted = FALSE)", Boolean.class, agentId);
        return Boolean.TRUE.equals(exists);
    }
}
