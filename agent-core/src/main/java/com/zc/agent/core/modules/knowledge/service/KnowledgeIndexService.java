package com.zc.agent.core.modules.knowledge.service;

import com.zc.agent.core.modules.knowledge.client.RegistryKnowledgeClient;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.knowledge.repository.KnowledgeRuntimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Knowledge 增量分块和索引任务执行服务。
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);
    private final RegistryKnowledgeClient client;
    private final KnowledgeRuntimeRepository repository;
    private final KnowledgeEmbeddingService embeddingService;

    public KnowledgeIndexService(RegistryKnowledgeClient client, KnowledgeRuntimeRepository repository,
                                 KnowledgeEmbeddingService embeddingService) {
        this.client = client;
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /** 定时领取并执行注册中心发布的索引任务。 */
    @Scheduled(fixedDelayString = "${core.knowledge.index-poll-ms:10000}")
    public void poll() {
        KnowledgeModels.RegistryIndexTask task;
        try {
            task = client.claimTask();
        } catch (Exception exception) {
            log.debug("Knowledge 索引任务领取失败，等待下次调度：{}", exception.getMessage());
            return;
        }
        if (task != null) {
            execute(task);
        }
    }

    /** 执行一次完整或指定文档的增量索引。 */
    public void execute(KnowledgeModels.RegistryIndexTask task) {
        repository.startTask(task);
        int processed = 0;
        int chunks = 0;
        int total = task.totalDocuments() == null ? 0 : task.totalDocuments();
        try {
            KnowledgeModels.RegistryKnowledge knowledge = client.getKnowledge(task.knowledgeId());
            List<KnowledgeModels.RegistryDocument> selected = knowledge.documents().stream()
                    .filter(document -> task.documentIds() == null || task.documentIds().isEmpty()
                            || task.documentIds().contains(document.documentId()))
                    .toList();
            total = selected.size();
            List<KnowledgeRuntimeRepository.IndexedDocument> indexed = new ArrayList<>();
            for (KnowledgeModels.RegistryDocument document : selected) {
                List<KnowledgeRuntimeRepository.IndexedChunk> documentChunks = chunk(document,
                        knowledge.chunkSize(), knowledge.chunkOverlap());
                indexed.add(new KnowledgeRuntimeRepository.IndexedDocument(document, documentChunks));
                processed++;
                chunks += documentChunks.size();
            }
            repository.indexKnowledge(knowledge, indexed);
            repository.finishTask(task.taskId(), "succeeded", total, processed, chunks, null);
            client.completeTask(task.taskId(), "succeeded", total, processed, chunks, null);
            log.info("Knowledge 索引完成：taskCode={}, knowledgeId={}, documents={}, chunks={}",
                    task.taskCode(), task.knowledgeId(), processed, chunks);
        } catch (Exception exception) {
            String error = abbreviate(exception.getMessage());
            repository.finishTask(task.taskId(), "failed", total, processed, chunks, error);
            try {
                client.completeTask(task.taskId(), "failed", total, processed, chunks, error);
            } catch (Exception callbackException) {
                log.error("Knowledge 索引失败且状态回写失败：taskCode={}", task.taskCode(), callbackException);
            }
            log.error("Knowledge 索引失败：taskCode={}", task.taskCode(), exception);
        }
    }

    private List<KnowledgeRuntimeRepository.IndexedChunk> chunk(KnowledgeModels.RegistryDocument document,
                                                                 Integer configuredSize, Integer configuredOverlap) {
        int size = configuredSize == null ? 800 : Math.max(200, configuredSize);
        int overlap = configuredOverlap == null ? 120 : Math.max(0, Math.min(configuredOverlap, size - 1));
        String content = document.content() == null ? "" : document.content().replace("\r\n", "\n").trim();
        List<KnowledgeRuntimeRepository.IndexedChunk> result = new ArrayList<>();
        int start = 0;
        int no = 1;
        while (start < content.length()) {
            int targetEnd = Math.min(content.length(), start + size);
            int end = findBoundary(content, start, targetEnd);
            String text = content.substring(start, end).trim();
            if (!text.isEmpty()) {
                result.add(new KnowledgeRuntimeRepository.IndexedChunk(no++, text, Math.max(1, text.length() / 3),
                        sha256(text), embeddingService.vectorLiteral(embeddingService.embed(text)),
                        Map.of("documentCode", document.documentCode(), "documentVersion", document.version())));
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private int findBoundary(String content, int start, int targetEnd) {
        if (targetEnd >= content.length()) {
            return content.length();
        }
        int minimum = start + (targetEnd - start) / 2;
        for (int index = targetEnd; index > minimum; index--) {
            char value = content.charAt(index - 1);
            if (value == '\n' || value == '。' || value == '！' || value == '？' || value == '.') {
                return index;
            }
        }
        return targetEnd;
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String abbreviate(String message) {
        String value = message == null ? "未知索引错误" : message;
        return value.length() <= 1900 ? value : value.substring(0, 1900);
    }
}
