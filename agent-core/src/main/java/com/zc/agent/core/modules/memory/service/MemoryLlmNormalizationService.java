package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.MemoryLlmProperties;
import com.zc.agent.core.modules.memory.model.MemoryUpdateDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Memory LLM 归一化服务。
 * 负责在规则提取结果基础上做轻量归一化和压缩，失败时自动回退到规则结果。
 */
@Service
public class MemoryLlmNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryLlmNormalizationService.class);

    private final MemoryLlmProperties memoryLlmProperties;
    private final ObjectMapper objectMapper;

    public MemoryLlmNormalizationService(MemoryLlmProperties memoryLlmProperties,
                                         ObjectMapper objectMapper) {
        this.memoryLlmProperties = memoryLlmProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用 LLM 对规则草稿做归一化。
     *
     * @param draft 规则草稿
     * @return 归一化后的草稿；如果未启用或调用失败，则直接返回原草稿
     */
    public MemoryUpdateDraft normalize(MemoryUpdateDraft draft) {
        if (!isReady()) {
            return draft;
        }
        try {
            String responseText = invokeLlm(buildRequestBody(draft));
            LlmNormalizedMemoryResponse normalizedMemoryResponse = parseResponse(responseText);
            return mergeDraft(draft, normalizedMemoryResponse);
        } catch (Exception exception) {
            log.warn("Memory LLM 归一化失败，回退规则结果：{}", exception.getMessage());
            return draft;
        }
    }

    private boolean isReady() {
        return memoryLlmProperties.isEnabled()
                && hasText(memoryLlmProperties.getBaseUrl())
                && hasText(memoryLlmProperties.getApiKey())
                && hasText(memoryLlmProperties.getModel());
    }

    private String invokeLlm(String requestBody) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(memoryLlmProperties.getTimeoutSeconds()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(memoryLlmProperties.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(memoryLlmProperties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + memoryLlmProperties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Memory LLM 请求失败，status=" + response.statusCode() + ", body=" + response.body());
        }
        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull() || contentNode.asText().isBlank()) {
            throw new IllegalStateException("Memory LLM 返回内容为空");
        }
        return contentNode.asText();
    }

    private String buildRequestBody(MemoryUpdateDraft draft) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "model", memoryLlmProperties.getModel(),
                "temperature", memoryLlmProperties.getTemperature(),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        你是 Agent Memory 归一化助手。
                                        你只能基于输入里的规则草稿做轻量归一化、去重、压缩，不得编造新事实，不得新增输入中没有出现的目标或偏好。
                                        返回严格 JSON，不要输出 Markdown，不要输出解释。
                                        要求：
                                        1. conversationMemorySummary、taskMemorySummary、taskKeyFindings、taskFinalResponseSummary 保持简洁自然。
                                        2. conversationPreferenceSummary、userPreferenceSummary、userAttentionSummary、userFactSummary、userGoalSummary 使用中文短语，多个条目之间用全角分号“；”分隔。
                                        3. 对语义相近的条目做统一表达。
                                        4. 如果某字段不适合增强，保持原值。
                                        5. 不要改写 lastUserMessage 和 lastAssistantSummary。
                                        输出 JSON 字段固定为：
                                        conversationMemorySummary
                                        conversationPreferenceSummary
                                        userPreferenceSummary
                                        userAttentionSummary
                                        userFactSummary
                                        userGoalSummary
                                        taskMemorySummary
                                        taskKeyFindings
                                        taskFinalResponseSummary
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", objectMapper.writeValueAsString(draft)
                        )
                )
        );
        return objectMapper.writeValueAsString(payload);
    }

    private LlmNormalizedMemoryResponse parseResponse(String responseText) throws JsonProcessingException {
        String normalizedText = responseText.trim();
        if (normalizedText.startsWith("```")) {
            normalizedText = normalizedText.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }
        return objectMapper.readValue(normalizedText, LlmNormalizedMemoryResponse.class);
    }

    private MemoryUpdateDraft mergeDraft(MemoryUpdateDraft draft, LlmNormalizedMemoryResponse response) {
        return new MemoryUpdateDraft(
                draft.userMessage(),
                draft.assistantContent(),
                coalesce(response.getConversationMemorySummary(), draft.conversationMemorySummary()),
                coalesce(response.getConversationPreferenceSummary(), draft.conversationPreferenceSummary()),
                draft.lastUserMessage(),
                draft.lastAssistantSummary(),
                coalesce(response.getUserPreferenceSummary(), draft.userPreferenceSummary()),
                coalesce(response.getUserAttentionSummary(), draft.userAttentionSummary()),
                coalesce(response.getUserFactSummary(), draft.userFactSummary()),
                coalesce(response.getUserGoalSummary(), draft.userGoalSummary()),
                coalesce(response.getTaskMemorySummary(), draft.taskMemorySummary()),
                coalesce(response.getTaskKeyFindings(), draft.taskKeyFindings()),
                coalesce(response.getTaskFinalResponseSummary(), draft.taskFinalResponseSummary())
        );
    }

    private String coalesce(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /**
     * LLM 返回的归一化结果。
     */
    public static class LlmNormalizedMemoryResponse {

        private String conversationMemorySummary;
        private String conversationPreferenceSummary;
        private String userPreferenceSummary;
        private String userAttentionSummary;
        private String userFactSummary;
        private String userGoalSummary;
        private String taskMemorySummary;
        private String taskKeyFindings;
        private String taskFinalResponseSummary;

        public String getConversationMemorySummary() {
            return conversationMemorySummary;
        }

        public void setConversationMemorySummary(String conversationMemorySummary) {
            this.conversationMemorySummary = conversationMemorySummary;
        }

        public String getConversationPreferenceSummary() {
            return conversationPreferenceSummary;
        }

        public void setConversationPreferenceSummary(String conversationPreferenceSummary) {
            this.conversationPreferenceSummary = conversationPreferenceSummary;
        }

        public String getUserPreferenceSummary() {
            return userPreferenceSummary;
        }

        public void setUserPreferenceSummary(String userPreferenceSummary) {
            this.userPreferenceSummary = userPreferenceSummary;
        }

        public String getUserAttentionSummary() {
            return userAttentionSummary;
        }

        public void setUserAttentionSummary(String userAttentionSummary) {
            this.userAttentionSummary = userAttentionSummary;
        }

        public String getUserFactSummary() {
            return userFactSummary;
        }

        public void setUserFactSummary(String userFactSummary) {
            this.userFactSummary = userFactSummary;
        }

        public String getUserGoalSummary() {
            return userGoalSummary;
        }

        public void setUserGoalSummary(String userGoalSummary) {
            this.userGoalSummary = userGoalSummary;
        }

        public String getTaskMemorySummary() {
            return taskMemorySummary;
        }

        public void setTaskMemorySummary(String taskMemorySummary) {
            this.taskMemorySummary = taskMemorySummary;
        }

        public String getTaskKeyFindings() {
            return taskKeyFindings;
        }

        public void setTaskKeyFindings(String taskKeyFindings) {
            this.taskKeyFindings = taskKeyFindings;
        }

        public String getTaskFinalResponseSummary() {
            return taskFinalResponseSummary;
        }

        public void setTaskFinalResponseSummary(String taskFinalResponseSummary) {
            this.taskFinalResponseSummary = taskFinalResponseSummary;
        }
    }
}
