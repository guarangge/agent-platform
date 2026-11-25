package com.zc.agent.core.modules.knowledge.client;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.RegistryProperties;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 注册中心 Knowledge 运行时客户端。
 */
@Component
public class RegistryKnowledgeClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Registry-Service-Token";
    private final RestClient registryRestClient;
    private final RegistryProperties properties;

    public RegistryKnowledgeClient(RestClient registryRestClient, RegistryProperties properties) {
        this.registryRestClient = registryRestClient;
        this.properties = properties;
    }

    /** 查询 Agent 和角色授权后的知识快照。 */
    public KnowledgeModels.RegistryBundle getBundle(Long agentId, List<String> roleCodes) {
        Envelope<KnowledgeModels.RegistryBundle> envelope = registryRestClient.get()
                .uri(builder -> {
                    builder.path("/knowledge-bases/runtime/agents/{agentId}");
                    if (roleCodes != null) {
                        roleCodes.forEach(role -> builder.queryParam("roleCodes", role));
                    }
                    return builder.build(agentId);
                })
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() { });
        return requireData(envelope, "读取 Agent Knowledge 配置失败");
    }

    /** 领取索引任务。 */
    public KnowledgeModels.RegistryIndexTask claimTask() {
        Envelope<KnowledgeModels.RegistryIndexTask> envelope = registryRestClient.post()
                .uri("/knowledge-bases/runtime/index-tasks/claim")
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() { });
        return envelope == null || envelope.code() != 200 ? null : envelope.data();
    }

    /** 按索引任务读取知识快照。 */
    public KnowledgeModels.RegistryKnowledge getKnowledge(Long knowledgeId) {
        Envelope<KnowledgeModels.RegistryKnowledge> envelope = registryRestClient.get()
                .uri("/knowledge-bases/runtime/{knowledgeId}", knowledgeId)
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() { });
        return requireData(envelope, "读取 Knowledge 索引快照失败");
    }

    /** 回写索引任务结果。 */
    public void completeTask(Long taskId, String status, int totalDocuments, int processedDocuments,
                             int totalChunks, String errorMessage) {
        registryRestClient.put().uri("/knowledge-bases/runtime/index-tasks/{taskId}/complete", taskId)
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompleteTaskRequest(status, totalDocuments, processedDocuments, totalChunks, errorMessage))
                .retrieve().toBodilessEntity();
    }

    private <T> T requireData(Envelope<T> envelope, String message) {
        if (envelope == null || envelope.code() != 200 || envelope.data() == null) {
            throw new BusinessException(message);
        }
        return envelope.data();
    }

    private record Envelope<T>(int code, String message, T data) {
    }

    private record CompleteTaskRequest(String status, int totalDocuments, int processedDocuments,
                                       int totalChunks, String errorMessage) {
    }
}
