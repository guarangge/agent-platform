package com.zc.agent.core.modules.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Knowledge 运行时领域契约。
 */
public final class KnowledgeModels {

    private KnowledgeModels() {
    }

    /** 注册中心文档快照。 */
    public record RegistryDocument(Long documentId, String documentCode, String documentName, String contentType,
                                   String sourceUri, String content, String contentHash, Integer version, JsonNode metadata) {
    }

    /** 注册中心知识库快照。 */
    public record RegistryKnowledge(Long knowledgeId, String knowledgeCode, String knowledgeName,
                                    String embeddingModelCode, Integer chunkSize, Integer chunkOverlap,
                                    Integer retrievalTopK, Double similarityThreshold, JsonNode metadata,
                                    LocalDateTime updatedAt, List<RegistryDocument> documents) {
    }

    /** Agent 有效知识库快照。 */
    public record RegistryBundle(Long agentId, List<RegistryKnowledge> knowledgeBases) {
    }

    /** 注册中心索引任务。 */
    public record RegistryIndexTask(Long taskId, String taskCode, Long knowledgeId, String triggerType,
                                    List<Long> documentIds, String status, Integer totalDocuments,
                                    Integer processedDocuments, Integer totalChunks, String errorMessage,
                                    LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt) {
    }

    /** 检索请求。 */
    public record SearchRequest(
            @NotNull Long agentId,
            Long knowledgeId,
            @NotBlank String query,
            @Min(1) @Max(50) Integer topK,
            Long conversationId,
            Long taskId,
            String traceId
    ) {
    }

    /** 标准知识引用。 */
    public record Citation(Long knowledgeId, String knowledgeCode, Long documentId, String documentCode,
                           String documentName, Integer chunkNo, String sourceUri, String excerpt,
                           Double keywordScore, Double vectorScore, Double score) {
    }

    /** 检索响应。 */
    public record SearchResponse(String retrievalCode, String query, int hitCount, long latencyMs,
                                 List<Citation> citations, String promptContext) {
    }

    /** 已索引分块查看记录。 */
    public record RuntimeChunk(Long chunkId, Long knowledgeId, String knowledgeCode, Long documentId,
                               String documentCode, String documentName, Integer chunkNo, String chunkText,
                               Integer tokenEstimate, LocalDateTime createdAt) {
    }

    /** 评测用例。 */
    public record EvalCase(@NotBlank String caseCode, @NotBlank String query,
                           @NotEmpty List<Long> expectedDocumentIds, @Min(1) @Max(50) Integer topK) {
    }

    /** 检索评测请求。 */
    public record EvalRequest(@NotNull Long agentId, Long knowledgeId,
                              @NotEmpty List<@Valid EvalCase> cases) {
    }

    /** 单条评测结果。 */
    public record EvalCaseResult(String caseCode, String query, List<Long> expectedDocumentIds,
                                 List<Long> actualDocumentIds, boolean hit, Integer firstRelevantRank,
                                 double reciprocalRank, long latencyMs) {
    }

    /** 检索评测响应。 */
    public record EvalResponse(String runCode, Long agentId, Long knowledgeId, int caseCount, int hitCaseCount,
                               double recallAtK, double meanReciprocalRank, double averageLatencyMs,
                               List<EvalCaseResult> cases, LocalDateTime createdAt) {
    }

    /** 历史评测运行摘要。 */
    public record EvalRunSummary(Long runId, String runCode, Long agentId, Long knowledgeId, Integer caseCount,
                                 Integer hitCaseCount, Double recallAtK, Double meanReciprocalRank,
                                 Double averageLatencyMs, LocalDateTime createdAt) {
    }
}
