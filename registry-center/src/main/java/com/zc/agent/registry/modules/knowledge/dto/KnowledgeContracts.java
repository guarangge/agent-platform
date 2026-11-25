package com.zc.agent.registry.modules.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Knowledge 模块接口契约。
 */
public final class KnowledgeContracts {

    private KnowledgeContracts() {
    }

    /** 知识库分页查询条件。 */
    public record PageQuery(Integer pageNum, Integer pageSize, String keyword, String status) {
    }

    /** 新增或更新知识库请求。 */
    public record SaveKnowledgeRequest(
            @NotBlank @Size(max = 64) String knowledgeCode,
            @NotBlank @Size(max = 128) String knowledgeName,
            @Size(max = 500) String description,
            @Size(max = 64) String embeddingModelCode,
            @NotNull @Min(200) @Max(4000) Integer chunkSize,
            @NotNull @Min(0) @Max(1000) Integer chunkOverlap,
            @NotNull @Min(1) @Max(50) Integer retrievalTopK,
            @NotNull @Min(0) @Max(1) Double similarityThreshold,
            @NotBlank String status,
            JsonNode metadata,
            @Size(max = 500) String remark
    ) {
    }

    /** 新增或更新知识来源请求。 */
    public record SaveSourceRequest(
            @NotBlank @Size(max = 64) String sourceCode,
            @NotBlank @Size(max = 128) String sourceName,
            @NotBlank String sourceType,
            @Size(max = 1000) String sourceUri,
            @NotBlank String syncMode,
            JsonNode config,
            @NotBlank String status,
            JsonNode metadata
    ) {
    }

    /** 新增或更新知识文档请求。 */
    public record SaveDocumentRequest(
            Long sourceId,
            @NotBlank @Size(max = 128) String documentCode,
            @NotBlank @Size(max = 255) String documentName,
            @NotBlank @Size(max = 64) String contentType,
            @Size(max = 1000) String sourceUri,
            @NotBlank String content,
            @NotBlank String status,
            JsonNode metadata
    ) {
    }

    /** Knowledge 绑定与权限请求。 */
    public record UpdateBindingsRequest(List<Long> agentIds, List<Long> roleIds) {
    }

    /** 创建增量或全量索引任务请求。 */
    public record CreateIndexTaskRequest(List<Long> documentIds, String triggerType) {
    }

    /** 运行时索引任务结果回写请求。 */
    public record CompleteIndexTaskRequest(
            @NotBlank String status,
            @NotNull @Min(0) Integer totalDocuments,
            @NotNull @Min(0) Integer processedDocuments,
            @NotNull @Min(0) Integer totalChunks,
            @Size(max = 2000) String errorMessage
    ) {
    }

    /** 知识库分页记录。 */
    public record KnowledgePageItem(
            Long knowledgeId,
            String knowledgeCode,
            String knowledgeName,
            String description,
            String embeddingModelCode,
            Integer documentCount,
            Integer indexedDocumentCount,
            String status,
            LocalDateTime updatedAt
    ) {
    }

    /** 知识来源详情。 */
    public record SourceItem(
            Long sourceId,
            String sourceCode,
            String sourceName,
            String sourceType,
            String sourceUri,
            String syncMode,
            JsonNode config,
            String status,
            LocalDateTime lastSyncedAt,
            JsonNode metadata
    ) {
    }

    /** 知识文档详情。 */
    public record DocumentItem(
            Long documentId,
            Long sourceId,
            String documentCode,
            String documentName,
            String contentType,
            String sourceUri,
            String content,
            String contentHash,
            Integer version,
            String status,
            JsonNode metadata,
            LocalDateTime indexedAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 索引任务详情。 */
    public record IndexTaskItem(
            Long taskId,
            String taskCode,
            Long knowledgeId,
            String triggerType,
            List<Long> documentIds,
            String status,
            Integer totalDocuments,
            Integer processedDocuments,
            Integer totalChunks,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {
    }

    /** Knowledge 完整管理详情。 */
    public record KnowledgeDetail(
            Long knowledgeId,
            String knowledgeCode,
            String knowledgeName,
            String description,
            String embeddingModelCode,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer retrievalTopK,
            Double similarityThreshold,
            String status,
            JsonNode metadata,
            String remark,
            List<SourceItem> sources,
            List<DocumentItem> documents,
            List<Long> agentIds,
            List<Long> roleIds,
            List<IndexTaskItem> indexTasks,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 运行时可索引文档快照。 */
    public record RuntimeDocument(
            Long documentId,
            String documentCode,
            String documentName,
            String contentType,
            String sourceUri,
            String content,
            String contentHash,
            Integer version,
            JsonNode metadata
    ) {
    }

    /** 运行时知识库快照。 */
    public record RuntimeKnowledge(
            Long knowledgeId,
            String knowledgeCode,
            String knowledgeName,
            String embeddingModelCode,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer retrievalTopK,
            Double similarityThreshold,
            JsonNode metadata,
            LocalDateTime updatedAt,
            List<RuntimeDocument> documents
    ) {
    }

    /** 运行时 Agent 有效知识列表。 */
    public record RuntimeKnowledgeBundle(Long agentId, List<RuntimeKnowledge> knowledgeBases) {
    }

    /** Agent 或角色绑定候选。 */
    public record BindingOption(Long id, String code, String name, String status) {
    }

    /** Knowledge 权限绑定候选集合。 */
    public record BindingOptions(List<BindingOption> agents, List<BindingOption> roles) {
    }
}
