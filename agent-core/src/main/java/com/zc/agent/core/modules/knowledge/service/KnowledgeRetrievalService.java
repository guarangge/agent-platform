package com.zc.agent.core.modules.knowledge.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.knowledge.client.RegistryKnowledgeClient;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.knowledge.repository.KnowledgeRuntimeRepository;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Knowledge 权限过滤、混合检索、引用与评测服务。
 */
@Service
public class KnowledgeRetrievalService {

    private final RegistryKnowledgeClient client;
    private final KnowledgeRuntimeRepository repository;
    private final KnowledgeEmbeddingService embeddingService;

    public KnowledgeRetrievalService(RegistryKnowledgeClient client, KnowledgeRuntimeRepository repository,
                                     KnowledgeEmbeddingService embeddingService) {
        this.client = client;
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /** 执行一次带 Agent、角色和知识库范围校验的检索。 */
    public KnowledgeModels.SearchResponse search(KnowledgeModels.SearchRequest request, SecurityUser user) {
        long start = System.nanoTime();
        KnowledgeModels.RegistryBundle bundle = client.getBundle(request.agentId(), user.roleCodes());
        List<Long> effectiveIds = bundle.knowledgeBases().stream().map(KnowledgeModels.RegistryKnowledge::knowledgeId).toList();
        if (request.knowledgeId() != null && !effectiveIds.contains(request.knowledgeId())) {
            throw new BusinessException("当前用户或 Agent 无权访问指定知识库");
        }
        int configuredTopK = bundle.knowledgeBases().stream()
                .filter(item -> request.knowledgeId() == null || request.knowledgeId().equals(item.knowledgeId()))
                .map(KnowledgeModels.RegistryKnowledge::retrievalTopK).filter(java.util.Objects::nonNull)
                .findFirst().orElse(5);
        double threshold = bundle.knowledgeBases().stream()
                .filter(item -> request.knowledgeId() == null || request.knowledgeId().equals(item.knowledgeId()))
                .map(KnowledgeModels.RegistryKnowledge::similarityThreshold).filter(java.util.Objects::nonNull)
                .findFirst().orElse(0.25D);
        int topK = request.topK() == null ? configuredTopK : Math.min(50, Math.max(1, request.topK()));
        List<KnowledgeModels.Citation> citations = repository.search(effectiveIds, request.knowledgeId(), request.query(),
                embeddingService.vectorLiteral(embeddingService.embed(request.query())), topK, threshold);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        String retrievalCode = code("KRET");
        repository.logRetrieval(retrievalCode, user.userId(), request, effectiveIds, topK, latencyMs, citations);
        return new KnowledgeModels.SearchResponse(retrievalCode, request.query(), citations.size(), latencyMs,
                citations, promptContext(citations));
    }

    /** 执行检索用例集并计算 Recall@K 与 MRR。 */
    public KnowledgeModels.EvalResponse evaluate(KnowledgeModels.EvalRequest request, SecurityUser user) {
        List<KnowledgeModels.EvalCaseResult> results = new ArrayList<>();
        for (KnowledgeModels.EvalCase evalCase : request.cases()) {
            KnowledgeModels.SearchResponse response = search(new KnowledgeModels.SearchRequest(
                    request.agentId(), request.knowledgeId(), evalCase.query(), evalCase.topK(), null, null, null), user);
            List<Long> actual = response.citations().stream().map(KnowledgeModels.Citation::documentId)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
            Integer firstRank = null;
            for (int index = 0; index < actual.size(); index++) {
                if (evalCase.expectedDocumentIds().contains(actual.get(index))) {
                    firstRank = index + 1;
                    break;
                }
            }
            results.add(new KnowledgeModels.EvalCaseResult(evalCase.caseCode(), evalCase.query(),
                    evalCase.expectedDocumentIds(), actual, firstRank != null, firstRank,
                    firstRank == null ? 0D : 1D / firstRank, response.latencyMs()));
        }
        int hits = (int) results.stream().filter(KnowledgeModels.EvalCaseResult::hit).count();
        double recall = results.isEmpty() ? 0D : (double) hits / results.size();
        double mrr = results.stream().mapToDouble(KnowledgeModels.EvalCaseResult::reciprocalRank).average().orElse(0D);
        double latency = results.stream().mapToLong(KnowledgeModels.EvalCaseResult::latencyMs).average().orElse(0D);
        KnowledgeModels.EvalResponse response = new KnowledgeModels.EvalResponse(code("KEVAL"), request.agentId(),
                request.knowledgeId(), results.size(), hits, recall, mrr, latency, results, LocalDateTime.now());
        repository.saveEval(response, user.userId());
        return response;
    }

    /** 查看已索引分块，仅允许查看当前 Agent 已授权知识库。 */
    public List<KnowledgeModels.RuntimeChunk> listChunks(Long agentId, Long knowledgeId, int pageNum, int pageSize,
                                                         SecurityUser user) {
        KnowledgeModels.RegistryBundle bundle = client.getBundle(agentId, user.roleCodes());
        if (bundle.knowledgeBases().stream().noneMatch(item -> item.knowledgeId().equals(knowledgeId))) {
            throw new BusinessException("当前用户或 Agent 无权查看指定知识库分块");
        }
        int size = Math.min(100, Math.max(1, pageSize));
        return repository.listChunks(knowledgeId, (Math.max(1, pageNum) - 1) * size, size);
    }

    /** 查询检索评测历史。 */
    public List<KnowledgeModels.EvalRunSummary> listEvalRuns(Long agentId) {
        return repository.listEvalRuns(agentId, 100);
    }

    private String promptContext(List<KnowledgeModels.Citation> citations) {
        if (citations.isEmpty()) {
            return "知识库未检索到可引用内容。不得虚构知识库结论，如使用模型常识必须明确说明。";
        }
        StringBuilder builder = new StringBuilder("以下内容来自已授权知识库。回答时优先依据这些内容，并使用 [K1]、[K2] 格式标注引用：");
        for (int index = 0; index < citations.size(); index++) {
            KnowledgeModels.Citation item = citations.get(index);
            builder.append("\n[K").append(index + 1).append("] ")
                    .append(item.documentName()).append(" / 分块 ").append(item.chunkNo())
                    .append(" / 来源 ").append(item.sourceUri() == null ? item.documentCode() : item.sourceUri())
                    .append("\n").append(item.excerpt());
        }
        return builder.toString();
    }

    private String code(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
