package com.zc.agent.core.modules.knowledge.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Knowledge 运行时索引、检索日志和评测仓储。
 */
@Repository
public class KnowledgeRuntimeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeRuntimeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 记录索引任务开始。 */
    public void startTask(KnowledgeModels.RegistryIndexTask task) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_runtime_index_tasks
                    (registry_task_id, task_code, registry_knowledge_id, status, total_documents, started_at)
                VALUES (?, ?, ?, 'running', ?, CURRENT_TIMESTAMP)
                ON CONFLICT (registry_task_id) DO UPDATE SET status = 'running', started_at = CURRENT_TIMESTAMP,
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP
                """, task.taskId(), task.taskCode(), task.knowledgeId(), task.totalDocuments());
    }

    /** 记录索引任务结束。 */
    public void finishTask(Long registryTaskId, String status, int total, int processed, int chunks, String error) {
        jdbcTemplate.update("""
                UPDATE knowledge_runtime_index_tasks SET status = ?, total_documents = ?, processed_documents = ?,
                    total_chunks = ?, error_message = ?, finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE registry_task_id = ?
                """, status, total, processed, chunks, error, registryTaskId);
    }

    /** 增量替换一个知识库的文档及分块。 */
    @Transactional
    public int indexKnowledge(KnowledgeModels.RegistryKnowledge knowledge,
                              List<IndexedDocument> documents) {
        Long runtimeKnowledgeId = upsertBase(knowledge);
        List<Long> activeRegistryDocumentIds = knowledge.documents().stream().map(KnowledgeModels.RegistryDocument::documentId).toList();
        if (activeRegistryDocumentIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM knowledge_runtime_documents WHERE runtime_knowledge_id = ?", runtimeKnowledgeId);
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(activeRegistryDocumentIds.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.add(runtimeKnowledgeId);
            params.addAll(activeRegistryDocumentIds);
            jdbcTemplate.update("DELETE FROM knowledge_runtime_documents WHERE runtime_knowledge_id = ? AND registry_document_id NOT IN (" + placeholders + ")",
                    params.toArray());
        }
        int chunkCount = 0;
        for (IndexedDocument document : documents) {
            Long runtimeDocumentId = upsertDocument(runtimeKnowledgeId, document.document());
            jdbcTemplate.update("DELETE FROM knowledge_runtime_chunks WHERE runtime_document_id = ?", runtimeDocumentId);
            for (IndexedChunk chunk : document.chunks()) {
                jdbcTemplate.update("""
                        INSERT INTO knowledge_runtime_chunks
                            (runtime_knowledge_id, runtime_document_id, chunk_no, chunk_text, token_estimate,
                             content_hash, embedding, metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?::jsonb)
                        """, runtimeKnowledgeId, runtimeDocumentId, chunk.chunkNo(), chunk.text(), chunk.tokenEstimate(),
                        chunk.contentHash(), chunk.vectorLiteral(), json(chunk.metadata()));
                chunkCount++;
            }
        }
        return chunkCount;
    }

    /** 对授权知识库执行关键词和向量混合检索。 */
    public List<KnowledgeModels.Citation> search(List<Long> registryKnowledgeIds, Long selectedKnowledgeId,
                                                 String query, String vectorLiteral, int topK, double threshold) {
        List<Long> effectiveIds = selectedKnowledgeId == null ? registryKnowledgeIds
                : registryKnowledgeIds.stream().filter(selectedKnowledgeId::equals).toList();
        if (effectiveIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(effectiveIds.size(), "?"));
        String sql = """
                SELECT kb.registry_knowledge_id, kb.knowledge_code, d.registry_document_id, d.document_code,
                       d.document_name, c.chunk_no, d.source_uri, c.chunk_text,
                       ts_rank_cd(c.search_vector, plainto_tsquery('simple', ?)) AS keyword_score,
                       (1 - (c.embedding <=> ?::vector)) AS vector_score,
                       (0.35 * ts_rank_cd(c.search_vector, plainto_tsquery('simple', ?))
                        + 0.65 * (1 - (c.embedding <=> ?::vector))) AS combined_score
                FROM knowledge_runtime_chunks c
                JOIN knowledge_runtime_bases kb ON kb.id = c.runtime_knowledge_id
                JOIN knowledge_runtime_documents d ON d.id = c.runtime_document_id
                WHERE kb.registry_knowledge_id IN (""" + placeholders + ") " +
                "AND (1 - (c.embedding <=> ?::vector)) >= ? ORDER BY combined_score DESC LIMIT ?";
        // SELECT 表达式中的占位符先于 WHERE IN，必须严格按 SQL 出现顺序绑定。
        List<Object> ordered = new ArrayList<>();
        ordered.add(query);
        ordered.add(vectorLiteral);
        ordered.add(query);
        ordered.add(vectorLiteral);
        ordered.addAll(effectiveIds);
        ordered.add(vectorLiteral);
        ordered.add(threshold);
        ordered.add(topK);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new KnowledgeModels.Citation(
                rs.getLong("registry_knowledge_id"), rs.getString("knowledge_code"),
                rs.getLong("registry_document_id"), rs.getString("document_code"), rs.getString("document_name"),
                rs.getInt("chunk_no"), rs.getString("source_uri"), excerpt(rs.getString("chunk_text")),
                rs.getDouble("keyword_score"), rs.getDouble("vector_score"), rs.getDouble("combined_score")
        ), ordered.toArray());
    }

    /** 写入检索审计日志。 */
    public void logRetrieval(String code, Long userId, KnowledgeModels.SearchRequest request, List<Long> knowledgeIds,
                             int topK, long latencyMs, List<KnowledgeModels.Citation> citations) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_retrieval_logs
                    (retrieval_code, user_id, agent_id, conversation_id, task_id, trace_id, query_text,
                     knowledge_ids, top_k, hit_count, latency_ms, citations)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb)
                """, code, userId, request.agentId(), request.conversationId(), request.taskId(), request.traceId(),
                request.query(), json(knowledgeIds), topK, citations.size(), latencyMs, json(citations));
    }

    /** 保存检索评测结果。 */
    public void saveEval(KnowledgeModels.EvalResponse response, Long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_eval_runs
                    (run_code, agent_id, knowledge_id, case_count, hit_case_count, recall_at_k,
                     mean_reciprocal_rank, average_latency_ms, cases, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, response.runCode(), response.agentId(), response.knowledgeId(), response.caseCount(),
                response.hitCaseCount(), response.recallAtK(), response.meanReciprocalRank(),
                response.averageLatencyMs(), json(response.cases()), operatorId);
    }

    /** 分页查看知识库实际分块。 */
    public List<KnowledgeModels.RuntimeChunk> listChunks(Long registryKnowledgeId, int offset, int limit) {
        return jdbcTemplate.query("""
                SELECT c.id, kb.registry_knowledge_id, kb.knowledge_code, d.registry_document_id, d.document_code,
                       d.document_name, c.chunk_no, c.chunk_text, c.token_estimate, c.created_at
                FROM knowledge_runtime_chunks c
                JOIN knowledge_runtime_bases kb ON kb.id = c.runtime_knowledge_id
                JOIN knowledge_runtime_documents d ON d.id = c.runtime_document_id
                WHERE kb.registry_knowledge_id = ? ORDER BY d.id, c.chunk_no OFFSET ? LIMIT ?
                """, (rs, rowNum) -> new KnowledgeModels.RuntimeChunk(
                rs.getLong("id"), rs.getLong("registry_knowledge_id"), rs.getString("knowledge_code"),
                rs.getLong("registry_document_id"), rs.getString("document_code"), rs.getString("document_name"),
                rs.getInt("chunk_no"), rs.getString("chunk_text"), rs.getInt("token_estimate"),
                rs.getTimestamp("created_at").toLocalDateTime()), registryKnowledgeId, offset, limit);
    }

    /** 查询历史检索评测运行。 */
    public List<KnowledgeModels.EvalRunSummary> listEvalRuns(Long agentId, int limit) {
        String sql = """
                SELECT id, run_code, agent_id, knowledge_id, case_count, hit_case_count, recall_at_k,
                       mean_reciprocal_rank, average_latency_ms, created_at FROM knowledge_eval_runs
                """ + (agentId == null ? "" : " WHERE agent_id = ?") + " ORDER BY created_at DESC LIMIT ?";
        Object[] params = agentId == null ? new Object[]{limit} : new Object[]{agentId, limit};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new KnowledgeModels.EvalRunSummary(
                rs.getLong("id"), rs.getString("run_code"), rs.getLong("agent_id"),
                rs.getObject("knowledge_id") == null ? null : rs.getLong("knowledge_id"), rs.getInt("case_count"),
                rs.getInt("hit_case_count"), rs.getDouble("recall_at_k"), rs.getDouble("mean_reciprocal_rank"),
                rs.getDouble("average_latency_ms"), rs.getTimestamp("created_at").toLocalDateTime()), params);
    }

    private Long upsertBase(KnowledgeModels.RegistryKnowledge knowledge) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_runtime_bases
                    (registry_knowledge_id, knowledge_code, knowledge_name, embedding_model_code, chunk_size,
                     chunk_overlap, retrieval_top_k, similarity_threshold, config_snapshot, registry_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (registry_knowledge_id) DO UPDATE SET knowledge_code = EXCLUDED.knowledge_code,
                    knowledge_name = EXCLUDED.knowledge_name, embedding_model_code = EXCLUDED.embedding_model_code,
                    chunk_size = EXCLUDED.chunk_size, chunk_overlap = EXCLUDED.chunk_overlap,
                    retrieval_top_k = EXCLUDED.retrieval_top_k, similarity_threshold = EXCLUDED.similarity_threshold,
                    config_snapshot = EXCLUDED.config_snapshot, registry_updated_at = EXCLUDED.registry_updated_at,
                    synced_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, knowledge.knowledgeId(), knowledge.knowledgeCode(), knowledge.knowledgeName(),
                knowledge.embeddingModelCode(), knowledge.chunkSize(), knowledge.chunkOverlap(), knowledge.retrievalTopK(),
                knowledge.similarityThreshold(), json(knowledge.metadata()), knowledge.updatedAt());
    }

    private Long upsertDocument(Long runtimeKnowledgeId, KnowledgeModels.RegistryDocument document) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_runtime_documents
                    (runtime_knowledge_id, registry_document_id, document_code, document_name, content_type,
                     source_uri, content_hash, document_version, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (registry_document_id) DO UPDATE SET runtime_knowledge_id = EXCLUDED.runtime_knowledge_id,
                    document_code = EXCLUDED.document_code, document_name = EXCLUDED.document_name,
                    content_type = EXCLUDED.content_type, source_uri = EXCLUDED.source_uri,
                    content_hash = EXCLUDED.content_hash, document_version = EXCLUDED.document_version,
                    metadata = EXCLUDED.metadata, indexed_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, runtimeKnowledgeId, document.documentId(), document.documentCode(), document.documentName(),
                document.contentType(), document.sourceUri(), document.contentHash(), document.version(), json(document.metadata()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Knowledge 运行时 JSON 序列化失败");
        }
    }

    private String excerpt(String text) {
        if (text == null || text.length() <= 600) {
            return text;
        }
        return text.substring(0, 600) + "...";
    }

    public record IndexedDocument(KnowledgeModels.RegistryDocument document, List<IndexedChunk> chunks) {
    }

    public record IndexedChunk(int chunkNo, String text, int tokenEstimate, String contentHash,
                               String vectorLiteral, Map<String, Object> metadata) {
    }
}
