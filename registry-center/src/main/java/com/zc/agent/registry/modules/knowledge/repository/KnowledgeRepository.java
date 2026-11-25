package com.zc.agent.registry.modules.knowledge.repository;

import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Knowledge 注册数据仓储。
 */
@Repository
public class KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 分页统计知识库。 */
    public long count(String keyword, String status) {
        Query query = pageWhere(keyword, status);
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM knowledge_bases kb " + query.sql(), Long.class,
                query.params().toArray());
    }

    /** 分页读取知识库。 */
    public List<KnowledgeContracts.KnowledgePageItem> page(String keyword, String status, int offset, int limit) {
        Query query = pageWhere(keyword, status);
        List<Object> params = new ArrayList<>(query.params());
        params.add(offset);
        params.add(limit);
        String sql = """
                SELECT kb.id, kb.knowledge_code, kb.knowledge_name, kb.description, kb.embedding_model_code,
                       kb.status, kb.updated_at,
                       COUNT(d.id) FILTER (WHERE d.is_deleted = FALSE) AS document_count,
                       COUNT(d.id) FILTER (WHERE d.is_deleted = FALSE AND d.indexed_at IS NOT NULL) AS indexed_document_count
                FROM knowledge_bases kb
                LEFT JOIN knowledge_documents d ON d.knowledge_id = kb.id
                """ + query.sql() + " GROUP BY kb.id ORDER BY kb.updated_at DESC OFFSET ? LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new KnowledgeContracts.KnowledgePageItem(
                rs.getLong("id"), rs.getString("knowledge_code"), rs.getString("knowledge_name"),
                rs.getString("description"), rs.getString("embedding_model_code"), rs.getInt("document_count"),
                rs.getInt("indexed_document_count"), rs.getString("status"), time(rs, "updated_at")
        ), params.toArray());
    }

    /** 新增知识库。 */
    public Long insertKnowledge(KnowledgeContracts.SaveKnowledgeRequest request, String metadata, Long operatorId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_bases
                    (knowledge_code, knowledge_name, description, embedding_model_code, chunk_size, chunk_overlap,
                     retrieval_top_k, similarity_threshold, status, metadata, remark, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                RETURNING id
                """, Long.class, request.knowledgeCode(), request.knowledgeName(), request.description(),
                request.embeddingModelCode(), request.chunkSize(), request.chunkOverlap(), request.retrievalTopK(),
                request.similarityThreshold(), request.status(), metadata, request.remark(), operatorId, operatorId);
    }

    /** 更新知识库。 */
    public int updateKnowledge(Long id, KnowledgeContracts.SaveKnowledgeRequest request, String metadata, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE knowledge_bases
                SET knowledge_code = ?, knowledge_name = ?, description = ?, embedding_model_code = ?, chunk_size = ?,
                    chunk_overlap = ?, retrieval_top_k = ?, similarity_threshold = ?, status = ?, metadata = ?::jsonb,
                    remark = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, request.knowledgeCode(), request.knowledgeName(), request.description(), request.embeddingModelCode(),
                request.chunkSize(), request.chunkOverlap(), request.retrievalTopK(), request.similarityThreshold(),
                request.status(), metadata, request.remark(), operatorId, id);
    }

    /** 查询知识库基础记录。 */
    public Optional<BaseRecord> findBase(Long id) {
        return jdbcTemplate.query("""
                SELECT id, knowledge_code, knowledge_name, description, embedding_model_code, chunk_size, chunk_overlap,
                       retrieval_top_k, similarity_threshold, status, metadata::text, remark, created_at, updated_at
                FROM knowledge_bases WHERE id = ? AND is_deleted = FALSE
                """, BASE_MAPPER, id).stream().findFirst();
    }

    /** 查询知识库编码是否存在。 */
    public Optional<Long> findIdByCode(String code) {
        return jdbcTemplate.query("SELECT id FROM knowledge_bases WHERE knowledge_code = ? AND is_deleted = FALSE",
                (rs, rowNum) -> rs.getLong(1), code).stream().findFirst();
    }

    /** 新增知识来源。 */
    public Long insertSource(Long knowledgeId, KnowledgeContracts.SaveSourceRequest request,
                             String config, String metadata, Long operatorId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_sources
                    (knowledge_id, source_code, source_name, source_type, source_uri, sync_mode, config, status,
                     metadata, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?) RETURNING id
                """, Long.class, knowledgeId, request.sourceCode(), request.sourceName(), request.sourceType(),
                request.sourceUri(), request.syncMode(), config, request.status(), metadata, operatorId, operatorId);
    }

    /** 更新知识来源。 */
    public int updateSource(Long knowledgeId, Long sourceId, KnowledgeContracts.SaveSourceRequest request,
                            String config, String metadata, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE knowledge_sources SET source_code = ?, source_name = ?, source_type = ?, source_uri = ?,
                    sync_mode = ?, config = ?::jsonb, status = ?, metadata = ?::jsonb,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND knowledge_id = ? AND is_deleted = FALSE
                """, request.sourceCode(), request.sourceName(), request.sourceType(), request.sourceUri(),
                request.syncMode(), config, request.status(), metadata, operatorId, sourceId, knowledgeId);
    }

    /** 查询知识来源。 */
    public List<SourceRecord> listSources(Long knowledgeId) {
        return jdbcTemplate.query("""
                SELECT id, source_code, source_name, source_type, source_uri, sync_mode, config::text, status,
                       last_synced_at, metadata::text
                FROM knowledge_sources WHERE knowledge_id = ? AND is_deleted = FALSE ORDER BY id
                """, SOURCE_MAPPER, knowledgeId);
    }

    /** 新增文档。 */
    public Long insertDocument(Long knowledgeId, KnowledgeContracts.SaveDocumentRequest request, String contentHash,
                               String metadata, Long operatorId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_documents
                    (knowledge_id, source_id, document_code, document_name, content_type, source_uri, content,
                     content_hash, status, metadata, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id
                """, Long.class, knowledgeId, request.sourceId(), request.documentCode(), request.documentName(),
                request.contentType(), request.sourceUri(), request.content(), contentHash, request.status(), metadata,
                operatorId, operatorId);
    }

    /** 更新文档并使旧索引失效。 */
    public int updateDocument(Long knowledgeId, Long documentId, KnowledgeContracts.SaveDocumentRequest request,
                              String contentHash, String metadata, Long operatorId) {
        return jdbcTemplate.update("""
                UPDATE knowledge_documents SET source_id = ?, document_code = ?, document_name = ?, content_type = ?,
                    source_uri = ?, content = ?, content_hash = ?, version = CASE WHEN content_hash <> ? THEN version + 1 ELSE version END,
                    indexed_at = CASE WHEN content_hash <> ? THEN NULL ELSE indexed_at END, status = ?, metadata = ?::jsonb,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND knowledge_id = ? AND is_deleted = FALSE
                """, request.sourceId(), request.documentCode(), request.documentName(), request.contentType(),
                request.sourceUri(), request.content(), contentHash, contentHash, contentHash, request.status(), metadata,
                operatorId, documentId, knowledgeId);
    }

    /** 查询文档。 */
    public List<DocumentRecord> listDocuments(Long knowledgeId, boolean enabledOnly) {
        String statusSql = enabledOnly ? " AND status = 'enabled'" : "";
        return jdbcTemplate.query("""
                SELECT id, source_id, document_code, document_name, content_type, source_uri, content, content_hash,
                       version, status, metadata::text, indexed_at, updated_at
                FROM knowledge_documents WHERE knowledge_id = ? AND is_deleted = FALSE
                """ + statusSql + " ORDER BY id", DOCUMENT_MAPPER, knowledgeId);
    }

    /** 按知识库和文档主键查询文档，避免跨知识库读取原始文件。 */
    public Optional<DocumentRecord> findDocument(Long knowledgeId, Long documentId) {
        return jdbcTemplate.query("""
                SELECT id, source_id, document_code, document_name, content_type, source_uri, content, content_hash,
                       version, status, metadata::text, indexed_at, updated_at
                FROM knowledge_documents
                WHERE id = ? AND knowledge_id = ? AND is_deleted = FALSE
                """, DOCUMENT_MAPPER, documentId, knowledgeId).stream().findFirst();
    }

    /** 覆盖 Agent 和角色绑定。 */
    public void replaceBindings(Long knowledgeId, List<Long> agentIds, List<Long> roleIds, Long operatorId) {
        jdbcTemplate.update("UPDATE knowledge_agent_rel SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE knowledge_id = ? AND is_deleted = FALSE", operatorId, knowledgeId);
        jdbcTemplate.update("UPDATE knowledge_role_rel SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE knowledge_id = ? AND is_deleted = FALSE", operatorId, knowledgeId);
        for (Long agentId : agentIds) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_agent_rel (knowledge_id, agent_id, created_by, updated_by)
                    VALUES (?, ?, ?, ?)
                    """, knowledgeId, agentId, operatorId, operatorId);
        }
        for (Long roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_role_rel (knowledge_id, role_id, created_by, updated_by)
                    VALUES (?, ?, ?, ?)
                    """, knowledgeId, roleId, operatorId, operatorId);
        }
    }

    public List<Long> listAgentIds(Long knowledgeId) {
        return jdbcTemplate.query("SELECT agent_id FROM knowledge_agent_rel WHERE knowledge_id = ? AND status = 'enabled' AND is_deleted = FALSE ORDER BY agent_id",
                (rs, rowNum) -> rs.getLong(1), knowledgeId);
    }

    public List<Long> listRoleIds(Long knowledgeId) {
        return jdbcTemplate.query("SELECT role_id FROM knowledge_role_rel WHERE knowledge_id = ? AND status = 'enabled' AND is_deleted = FALSE ORDER BY role_id",
                (rs, rowNum) -> rs.getLong(1), knowledgeId);
    }

    /** 查询 Knowledge 页面可用的 Agent 和角色绑定候选。 */
    public KnowledgeContracts.BindingOptions bindingOptions() {
        List<KnowledgeContracts.BindingOption> agents = jdbcTemplate.query(
                "SELECT id, agent_code, agent_name, status FROM agents WHERE is_deleted = FALSE ORDER BY agent_name",
                (rs, rowNum) -> new KnowledgeContracts.BindingOption(rs.getLong("id"), rs.getString("agent_code"),
                        rs.getString("agent_name"), rs.getString("status")));
        List<KnowledgeContracts.BindingOption> roles = jdbcTemplate.query(
                "SELECT id, role_code, role_name, status FROM roles WHERE is_deleted = FALSE ORDER BY role_name",
                (rs, rowNum) -> new KnowledgeContracts.BindingOption(rs.getLong("id"), rs.getString("role_code"),
                        rs.getString("role_name"), rs.getString("status")));
        return new KnowledgeContracts.BindingOptions(agents, roles);
    }

    /** 创建索引发布任务。 */
    public Long insertIndexTask(String taskCode, Long knowledgeId, String triggerType, String documentIds,
                                int totalDocuments, Long operatorId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_index_tasks
                    (task_code, knowledge_id, trigger_type, requested_document_ids, total_documents, created_by, updated_by)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?) RETURNING id
                """, Long.class, taskCode, knowledgeId, triggerType, documentIds, totalDocuments, operatorId, operatorId);
    }

    public List<IndexTaskRecord> listIndexTasks(Long knowledgeId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, task_code, knowledge_id, trigger_type, requested_document_ids::text, status,
                       total_documents, processed_documents, total_chunks, error_message, started_at, finished_at, created_at
                FROM knowledge_index_tasks WHERE knowledge_id = ? ORDER BY created_at DESC LIMIT ?
                """, INDEX_TASK_MAPPER, knowledgeId, limit);
    }

    /** 领取待执行任务，使用行锁避免多个运行实例重复领取。 */
    public Optional<IndexTaskRecord> claimPendingTask() {
        List<IndexTaskRecord> tasks = jdbcTemplate.query("""
                UPDATE knowledge_index_tasks SET status = 'running', started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = (
                    SELECT id FROM knowledge_index_tasks WHERE status = 'pending' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
                ) RETURNING id, task_code, knowledge_id, trigger_type, requested_document_ids::text, status,
                    total_documents, processed_documents, total_chunks, error_message, started_at, finished_at, created_at
                """, INDEX_TASK_MAPPER);
        return tasks.stream().findFirst();
    }

    /** 回写运行时索引结果。 */
    public int completeIndexTask(Long taskId, KnowledgeContracts.CompleteIndexTaskRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE knowledge_index_tasks SET status = ?, total_documents = ?, processed_documents = ?,
                    total_chunks = ?, error_message = ?, finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('running', 'pending')
                """, request.status(), request.totalDocuments(), request.processedDocuments(), request.totalChunks(),
                request.errorMessage(), taskId);
        if (updated > 0 && "succeeded".equals(request.status())) {
            jdbcTemplate.update("""
                    UPDATE knowledge_documents SET indexed_at = CURRENT_TIMESTAMP, updated_at = updated_at
                    WHERE knowledge_id = (SELECT knowledge_id FROM knowledge_index_tasks WHERE id = ?)
                      AND is_deleted = FALSE AND status = 'enabled'
                      AND (
                          (SELECT requested_document_ids FROM knowledge_index_tasks WHERE id = ?) = '[]'::jsonb
                          OR id IN (
                              SELECT value::text::bigint
                              FROM jsonb_array_elements((SELECT requested_document_ids FROM knowledge_index_tasks WHERE id = ?))
                          )
                      )
                    """, taskId, taskId, taskId);
        }
        return updated;
    }

    /** 查询 Agent 与角色权限匹配后的有效知识库。 */
    public List<BaseRecord> listRuntimeBases(Long agentId, List<String> roleCodes) {
        List<Object> params = new ArrayList<>();
        params.add(agentId);
        String rolePredicate;
        if (roleCodes == null || roleCodes.isEmpty()) {
            rolePredicate = "NOT EXISTS (SELECT 1 FROM knowledge_role_rel rr WHERE rr.knowledge_id = kb.id AND rr.status = 'enabled' AND rr.is_deleted = FALSE)";
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(roleCodes.size(), "?"));
            rolePredicate = "(NOT EXISTS (SELECT 1 FROM knowledge_role_rel rr WHERE rr.knowledge_id = kb.id AND rr.status = 'enabled' AND rr.is_deleted = FALSE) "
                    + "OR EXISTS (SELECT 1 FROM knowledge_role_rel rr JOIN roles r ON r.id = rr.role_id WHERE rr.knowledge_id = kb.id "
                    + "AND rr.status = 'enabled' AND rr.is_deleted = FALSE AND r.role_code IN (" + placeholders + ")))";
            params.addAll(roleCodes);
        }
        return jdbcTemplate.query("""
                SELECT kb.id, kb.knowledge_code, kb.knowledge_name, kb.description, kb.embedding_model_code,
                       kb.chunk_size, kb.chunk_overlap, kb.retrieval_top_k, kb.similarity_threshold, kb.status,
                       kb.metadata::text, kb.remark, kb.created_at, kb.updated_at
                FROM knowledge_bases kb
                JOIN knowledge_agent_rel ar ON ar.knowledge_id = kb.id AND ar.agent_id = ?
                     AND ar.status = 'enabled' AND ar.is_deleted = FALSE
                WHERE kb.status = 'enabled' AND kb.is_deleted = FALSE AND
                """ + rolePredicate + " ORDER BY ar.priority, kb.id", BASE_MAPPER, params.toArray());
    }

    private Query pageWhere(String keyword, String status) {
        StringBuilder sql = new StringBuilder("WHERE kb.is_deleted = FALSE");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (kb.knowledge_code ILIKE ? OR kb.knowledge_name ILIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND kb.status = ?");
            params.add(status.trim());
        }
        return new Query(sql.toString(), params);
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static final RowMapper<BaseRecord> BASE_MAPPER = (rs, rowNum) -> new BaseRecord(
            rs.getLong("id"), rs.getString("knowledge_code"), rs.getString("knowledge_name"), rs.getString("description"),
            rs.getString("embedding_model_code"), rs.getInt("chunk_size"), rs.getInt("chunk_overlap"),
            rs.getInt("retrieval_top_k"), rs.getDouble("similarity_threshold"), rs.getString("status"),
            rs.getString("metadata"), rs.getString("remark"), time(rs, "created_at"), time(rs, "updated_at")
    );
    private static final RowMapper<SourceRecord> SOURCE_MAPPER = (rs, rowNum) -> new SourceRecord(
            rs.getLong("id"), rs.getString("source_code"), rs.getString("source_name"), rs.getString("source_type"),
            rs.getString("source_uri"), rs.getString("sync_mode"), rs.getString("config"), rs.getString("status"),
            time(rs, "last_synced_at"), rs.getString("metadata")
    );
    private static final RowMapper<DocumentRecord> DOCUMENT_MAPPER = (rs, rowNum) -> new DocumentRecord(
            rs.getLong("id"), nullableLong(rs, "source_id"), rs.getString("document_code"), rs.getString("document_name"),
            rs.getString("content_type"), rs.getString("source_uri"), rs.getString("content"), rs.getString("content_hash"),
            rs.getInt("version"), rs.getString("status"), rs.getString("metadata"), time(rs, "indexed_at"), time(rs, "updated_at")
    );
    private static final RowMapper<IndexTaskRecord> INDEX_TASK_MAPPER = (rs, rowNum) -> new IndexTaskRecord(
            rs.getLong("id"), rs.getString("task_code"), rs.getLong("knowledge_id"), rs.getString("trigger_type"),
            rs.getString("requested_document_ids"), rs.getString("status"), rs.getInt("total_documents"),
            rs.getInt("processed_documents"), rs.getInt("total_chunks"), rs.getString("error_message"),
            time(rs, "started_at"), time(rs, "finished_at"), time(rs, "created_at")
    );

    public record BaseRecord(Long id, String code, String name, String description, String embeddingModelCode,
                             Integer chunkSize, Integer chunkOverlap, Integer retrievalTopK, Double similarityThreshold,
                             String status, String metadata, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
    public record SourceRecord(Long id, String code, String name, String type, String uri, String syncMode,
                               String config, String status, LocalDateTime lastSyncedAt, String metadata) {
    }
    public record DocumentRecord(Long id, Long sourceId, String code, String name, String contentType, String sourceUri,
                                 String content, String contentHash, Integer version, String status, String metadata,
                                 LocalDateTime indexedAt, LocalDateTime updatedAt) {
    }
    public record IndexTaskRecord(Long id, String code, Long knowledgeId, String triggerType, String documentIds,
                                  String status, Integer totalDocuments, Integer processedDocuments, Integer totalChunks,
                                  String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt) {
    }
    private record Query(String sql, List<Object> params) {
    }
}
