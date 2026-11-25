package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从步骤输出快照解析产物，并发布不包含文件正文的 Artifact 生命周期事件。
 */
@Service
public class ArtifactRuntimeEventService {

    private final ObjectMapper objectMapper;
    private final RuntimeEventPublisher eventPublisher;

    public ArtifactRuntimeEventService(ObjectMapper objectMapper,
                                       RuntimeEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 步骤成功后发布 artifact.created，产物正文仍只保存在业务步骤快照中。 */
    public void publishCreated(Long taskId, Long taskStepId, String outputSnapshot) {
        for (ArtifactDescriptor artifact : extractArtifacts(outputSnapshot)) {
            LinkedHashMap<String, Object> payload = basePayload(taskId, artifact, "created");
            payload.put("taskStepId", taskStepId);
            publish(RuntimeEventType.ARTIFACT_CREATED, payload);
        }
    }

    /** 最终回复确认受保护下载地址后发布 artifact.published。 */
    public void publishPublished(Long conversationId,
                                 Long taskId,
                                 List<ArtifactDescriptor> artifacts) {
        for (ArtifactDescriptor artifact : artifacts) {
            LinkedHashMap<String, Object> payload = basePayload(taskId, artifact, "published");
            payload.put("downloadable", true);
            payload.put("downloadUrl", buildDownloadUrl(conversationId, taskId, artifact.path()));
            publish(RuntimeEventType.ARTIFACT_PUBLISHED, payload);
        }
    }

    /** 从结构化步骤快照中提取去重后的产物元数据。 */
    public List<ArtifactDescriptor> extractArtifacts(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return List.of();
        }
        try {
            JsonNode artifactNodes = objectMapper.readTree(outputSnapshot).path("payload").path("artifacts");
            if (!artifactNodes.isArray()) {
                return List.of();
            }
            LinkedHashMap<String, ArtifactDescriptor> artifacts = new LinkedHashMap<>();
            for (JsonNode artifactNode : artifactNodes) {
                String artifactPath = artifactNode.path("path").asText("").trim();
                if (artifactPath.isEmpty()) {
                    continue;
                }
                long size = Math.max(artifactNode.path("size").asLong(0), 0);
                artifacts.putIfAbsent(artifactPath, new ArtifactDescriptor(
                        artifactPath,
                        ArtifactMetadataSupport.fileName(artifactPath),
                        size,
                        ArtifactMetadataSupport.mediaType(artifactPath)
                ));
            }
            return List.copyOf(new ArrayList<>(artifacts.values()));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 构建与会话下载接口一致的受保护地址。 */
    public String buildDownloadUrl(Long conversationId, Long taskId, String artifactPath) {
        String encodedPath = URLEncoder.encode(artifactPath, StandardCharsets.UTF_8).replace("+", "%20");
        return "/api/core/conversations/" + conversationId + "/tasks/" + taskId
                + "/artifacts?path=" + encodedPath;
    }

    private LinkedHashMap<String, Object> basePayload(Long taskId,
                                                       ArtifactDescriptor artifact,
                                                       String status) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactId", ArtifactMetadataSupport.artifactId(taskId, artifact.path()));
        payload.put("taskId", taskId);
        payload.put("path", artifact.path());
        payload.put("fileName", artifact.fileName());
        payload.put("size", artifact.size());
        payload.put("mediaType", artifact.mediaType());
        payload.put("status", status);
        return payload;
    }

    private void publish(RuntimeEventType eventType, Map<String, Object> payload) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode != null && !runCode.isBlank()) {
            eventPublisher.publishPayload(runCode, eventType, payload, Map.of());
        }
    }

    /**
     * @param path 产物在 Sandbox Workspace 中的相对路径
     * @param fileName 面向用户显示的文件名
     * @param size 文件字节数
     * @param mediaType 文件媒体类型
     */
    public record ArtifactDescriptor(String path, String fileName, long size, String mediaType) {
    }
}
