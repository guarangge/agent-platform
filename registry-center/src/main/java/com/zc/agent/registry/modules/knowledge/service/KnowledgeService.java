package com.zc.agent.registry.modules.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import com.zc.agent.registry.modules.knowledge.repository.KnowledgeRepository;
import com.zc.agent.registry.modules.model.service.ModelService;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Knowledge 注册、授权与发布服务。
 */
@Service
public class KnowledgeService {

    private static final List<String> BASE_STATUSES = List.of("draft", "enabled", "disabled");
    private static final List<String> ITEM_STATUSES = List.of("draft", "enabled", "disabled");
    private static final List<String> SOURCE_TYPES = List.of("manual", "url", "file", "database", "api");
    private static final List<String> SYNC_MODES = List.of("manual", "scheduled", "webhook");
    private final KnowledgeRepository repository;
    private final ObjectMapper objectMapper;
    private final ModelService modelService;

    public KnowledgeService(KnowledgeRepository repository, ObjectMapper objectMapper, ModelService modelService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.modelService = modelService;
    }

    /** 分页查询知识库。 */
    public PageResponse<KnowledgeContracts.KnowledgePageItem> page(KnowledgeContracts.PageQuery request) {
        int pageNum = request.pageNum() == null ? 1 : Math.max(1, request.pageNum());
        int pageSize = request.pageSize() == null ? 10 : Math.min(100, Math.max(1, request.pageSize()));
        String status = text(request.status());
        if (status != null) {
            validateValue("知识库状态", status, BASE_STATUSES);
        }
        long total = repository.count(text(request.keyword()), status);
        return new PageResponse<>(repository.page(text(request.keyword()), status, (pageNum - 1) * pageSize, pageSize),
                total, pageNum, pageSize);
    }

    /** 查询知识库完整详情。 */
    public KnowledgeContracts.KnowledgeDetail detail(Long knowledgeId) {
        KnowledgeRepository.BaseRecord base = getBase(knowledgeId);
        return new KnowledgeContracts.KnowledgeDetail(
                base.id(), base.code(), base.name(), base.description(), base.embeddingModelCode(), base.chunkSize(),
                base.chunkOverlap(), base.retrievalTopK(), base.similarityThreshold(), base.status(), json(base.metadata()),
                base.remark(), repository.listSources(knowledgeId).stream().map(this::source).toList(),
                repository.listDocuments(knowledgeId, false).stream().map(this::document).toList(),
                repository.listAgentIds(knowledgeId), repository.listRoleIds(knowledgeId),
                repository.listIndexTasks(knowledgeId, 50).stream().map(this::indexTask).toList(),
                base.createdAt(), base.updatedAt()
        );
    }

    /** 创建知识库。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail create(KnowledgeContracts.SaveKnowledgeRequest request, SecurityUser user) {
        KnowledgeContracts.SaveKnowledgeRequest normalized = normalize(request);
        validateBase(normalized, null);
        Long id = repository.insertKnowledge(normalized, jsonText(normalized.metadata()), user.userId());
        return detail(id);
    }

    /** 更新知识库。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail update(Long knowledgeId, KnowledgeContracts.SaveKnowledgeRequest request,
                                                      SecurityUser user) {
        getBase(knowledgeId);
        KnowledgeContracts.SaveKnowledgeRequest normalized = normalize(request);
        validateBase(normalized, knowledgeId);
        repository.updateKnowledge(knowledgeId, normalized, jsonText(normalized.metadata()), user.userId());
        return detail(knowledgeId);
    }

    /** 新增知识来源。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail createSource(Long knowledgeId,
                                                            KnowledgeContracts.SaveSourceRequest request,
                                                            SecurityUser user) {
        getBase(knowledgeId);
        validateSource(request);
        repository.insertSource(knowledgeId, request, jsonText(request.config()), jsonText(request.metadata()), user.userId());
        return detail(knowledgeId);
    }

    /** 更新知识来源。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail updateSource(Long knowledgeId, Long sourceId,
                                                            KnowledgeContracts.SaveSourceRequest request,
                                                            SecurityUser user) {
        getBase(knowledgeId);
        validateSource(request);
        if (repository.updateSource(knowledgeId, sourceId, request, jsonText(request.config()),
                jsonText(request.metadata()), user.userId()) <= 0) {
            throw new BusinessException("知识来源不存在");
        }
        return detail(knowledgeId);
    }

    /** 新增知识文档。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail createDocument(Long knowledgeId,
                                                              KnowledgeContracts.SaveDocumentRequest request,
                                                              SecurityUser user) {
        getBase(knowledgeId);
        validateDocument(request);
        repository.insertDocument(knowledgeId, request, sha256(request.content()), jsonText(request.metadata()), user.userId());
        return detail(knowledgeId);
    }

    /** 更新知识文档，内容变化时自动增加版本并使索引失效。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail updateDocument(Long knowledgeId, Long documentId,
                                                              KnowledgeContracts.SaveDocumentRequest request,
                                                              SecurityUser user) {
        getBase(knowledgeId);
        validateDocument(request);
        if (repository.updateDocument(knowledgeId, documentId, request, sha256(request.content()),
                jsonText(request.metadata()), user.userId()) <= 0) {
            throw new BusinessException("知识文档不存在");
        }
        return detail(knowledgeId);
    }

    /** 覆盖知识库的 Agent 绑定和角色权限。 */
    @Transactional
    public KnowledgeContracts.KnowledgeDetail updateBindings(Long knowledgeId,
                                                              KnowledgeContracts.UpdateBindingsRequest request,
                                                              SecurityUser user) {
        getBase(knowledgeId);
        repository.replaceBindings(knowledgeId, distinctIds(request.agentIds()), distinctIds(request.roleIds()), user.userId());
        return detail(knowledgeId);
    }

    /** 创建索引发布任务。 */
    @Transactional
    public KnowledgeContracts.IndexTaskItem createIndexTask(Long knowledgeId,
                                                             KnowledgeContracts.CreateIndexTaskRequest request,
                                                             SecurityUser user) {
        getBase(knowledgeId);
        List<Long> documentIds = request == null ? List.of() : distinctIds(request.documentIds());
        String triggerType = request == null || text(request.triggerType()) == null ? "manual" : text(request.triggerType());
        List<KnowledgeRepository.DocumentRecord> documents = repository.listDocuments(knowledgeId, true);
        if (documents.isEmpty()) {
            throw new BusinessException("知识库没有可索引的启用文档");
        }
        if (!documentIds.isEmpty() && documentIds.stream().anyMatch(id -> documents.stream().noneMatch(doc -> doc.id().equals(id)))) {
            throw new BusinessException("索引任务包含无效或未启用文档");
        }
        int total = documentIds.isEmpty() ? documents.size() : documentIds.size();
        String taskCode = "KIDX_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Long taskId = repository.insertIndexTask(taskCode, knowledgeId, triggerType, jsonText(documentIds), total, user.userId());
        return repository.listIndexTasks(knowledgeId, 50).stream()
                .filter(item -> item.id().equals(taskId)).findFirst().map(this::indexTask)
                .orElseThrow(() -> new BusinessException("索引任务创建失败"));
    }

    /** 运行时领取一个待执行索引任务。 */
    @Transactional
    public KnowledgeContracts.IndexTaskItem claimIndexTask() {
        return repository.claimPendingTask().map(this::indexTask).orElse(null);
    }

    /** 运行时回写索引结果。 */
    @Transactional
    public void completeIndexTask(Long taskId, KnowledgeContracts.CompleteIndexTaskRequest request) {
        validateValue("索引任务状态", request.status(), List.of("succeeded", "failed"));
        if (repository.completeIndexTask(taskId, request) <= 0) {
            throw new BusinessException("索引任务不存在或状态不允许回写");
        }
    }

    /** 解析 Agent 和角色授权后的运行时知识快照。 */
    public KnowledgeContracts.RuntimeKnowledgeBundle runtimeBundle(Long agentId, List<String> roleCodes) {
        List<KnowledgeContracts.RuntimeKnowledge> bases = repository.listRuntimeBases(agentId,
                        roleCodes == null ? List.of() : roleCodes.stream().map(String::trim).filter(item -> !item.isEmpty()).distinct().toList())
                .stream().map(base -> new KnowledgeContracts.RuntimeKnowledge(
                        base.id(), base.code(), base.name(), base.embeddingModelCode(), base.chunkSize(), base.chunkOverlap(),
                        base.retrievalTopK(), base.similarityThreshold(), json(base.metadata()), base.updatedAt(),
                        repository.listDocuments(base.id(), true).stream().map(item -> new KnowledgeContracts.RuntimeDocument(
                                item.id(), item.code(), item.name(), item.contentType(), item.sourceUri(), item.content(),
                                item.contentHash(), item.version(), json(item.metadata())
                        )).toList()
                )).toList();
        return new KnowledgeContracts.RuntimeKnowledgeBundle(agentId, bases);
    }

    /** 按索引任务读取不受用户权限过滤的运行时知识快照。 */
    public KnowledgeContracts.RuntimeKnowledge runtimeKnowledge(Long knowledgeId) {
        KnowledgeRepository.BaseRecord base = getBase(knowledgeId);
        return new KnowledgeContracts.RuntimeKnowledge(
                base.id(), base.code(), base.name(), base.embeddingModelCode(), base.chunkSize(), base.chunkOverlap(),
                base.retrievalTopK(), base.similarityThreshold(), json(base.metadata()), base.updatedAt(),
                repository.listDocuments(base.id(), true).stream().map(item -> new KnowledgeContracts.RuntimeDocument(
                        item.id(), item.code(), item.name(), item.contentType(), item.sourceUri(), item.content(),
                        item.contentHash(), item.version(), json(item.metadata())
                )).toList()
        );
    }

    /** 查询 Knowledge 管理页面绑定候选。 */
    public KnowledgeContracts.BindingOptions bindingOptions() {
        return repository.bindingOptions();
    }

    private KnowledgeContracts.SaveKnowledgeRequest normalize(KnowledgeContracts.SaveKnowledgeRequest request) {
        return new KnowledgeContracts.SaveKnowledgeRequest(request.knowledgeCode().trim(), request.knowledgeName().trim(),
                text(request.description()), text(request.embeddingModelCode()), request.chunkSize(), request.chunkOverlap(),
                request.retrievalTopK(), request.similarityThreshold(), request.status().trim(), object(request.metadata()),
                text(request.remark()));
    }

    private void validateBase(KnowledgeContracts.SaveKnowledgeRequest request, Long excludeId) {
        validateValue("知识库状态", request.status(), BASE_STATUSES);
        if (request.chunkOverlap() >= request.chunkSize()) {
            throw new BusinessException("分块重叠长度必须小于分块长度");
        }
        if (request.embeddingModelCode() != null
                && !modelService.existsEnabledEmbeddingModelCode(request.embeddingModelCode())) {
            throw new BusinessException("Embedding 模型不存在、未启用或模型类型不正确");
        }
        repository.findIdByCode(request.knowledgeCode()).ifPresent(id -> {
            if (excludeId == null || !excludeId.equals(id)) {
                throw new BusinessException("知识库编码已存在");
            }
        });
    }

    private void validateSource(KnowledgeContracts.SaveSourceRequest request) {
        validateValue("知识来源类型", request.sourceType(), SOURCE_TYPES);
        validateValue("同步模式", request.syncMode(), SYNC_MODES);
        validateValue("知识来源状态", request.status(), ITEM_STATUSES);
        object(request.config());
        object(request.metadata());
    }

    private void validateDocument(KnowledgeContracts.SaveDocumentRequest request) {
        validateValue("知识文档状态", request.status(), ITEM_STATUSES);
        if (request.content().length() > 5_000_000) {
            throw new BusinessException("单个知识文档内容不能超过 500 万字符");
        }
        object(request.metadata());
    }

    private KnowledgeRepository.BaseRecord getBase(Long id) {
        return repository.findBase(id).orElseThrow(() -> new BusinessException("知识库不存在"));
    }

    private KnowledgeContracts.SourceItem source(KnowledgeRepository.SourceRecord record) {
        return new KnowledgeContracts.SourceItem(record.id(), record.code(), record.name(), record.type(), record.uri(),
                record.syncMode(), json(record.config()), record.status(), record.lastSyncedAt(), json(record.metadata()));
    }

    private KnowledgeContracts.DocumentItem document(KnowledgeRepository.DocumentRecord record) {
        return new KnowledgeContracts.DocumentItem(record.id(), record.sourceId(), record.code(), record.name(),
                record.contentType(), record.sourceUri(), record.content(), record.contentHash(), record.version(),
                record.status(), json(record.metadata()), record.indexedAt(), record.updatedAt());
    }

    private KnowledgeContracts.IndexTaskItem indexTask(KnowledgeRepository.IndexTaskRecord record) {
        return new KnowledgeContracts.IndexTaskItem(record.id(), record.code(), record.knowledgeId(), record.triggerType(),
                longList(record.documentIds()), record.status(), record.totalDocuments(), record.processedDocuments(),
                record.totalChunks(), record.errorMessage(), record.startedAt(), record.finishedAt(), record.createdAt());
    }

    private JsonNode object(JsonNode node) {
        if (node == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!node.isObject()) {
            throw new BusinessException("扩展配置必须为 JSON 对象");
        }
        return node;
    }

    private JsonNode json(String value) {
        try {
            return value == null ? JsonNodeFactory.instance.objectNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Knowledge JSON 数据格式不合法");
        }
    }

    private String jsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? JsonNodeFactory.instance.objectNode() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Knowledge JSON 数据格式不合法");
        }
    }

    private List<Long> longList(String value) {
        try {
            if (value == null) {
                return List.of();
            }
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("索引文档 ID 格式不合法");
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).filter(id -> id > 0).distinct().toList();
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private void validateValue(String field, String value, List<String> allowed) {
        if (value == null || !allowed.contains(value.trim())) {
            throw new BusinessException(field + "不合法，可选值：" + String.join(", ", allowed));
        }
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
