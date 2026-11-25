package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.dto.CreateConversationRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationMemoryPolicyRequest;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.memory.service.ConversationMemoryPolicyResolver;
import org.springframework.stereotype.Service;

import java.util.List;

/** 负责会话记忆与上下文压缩配置的校验、继承和 metadata 合并。 */
@Service
public class ConversationPolicyService {
    private static final List<String> ALLOWED_USER_MEMORY_MODES = List.of(
            ConversationMemoryPolicyResolver.MODE_OFF,
            ConversationMemoryPolicyResolver.MODE_SUMMARY_ONLY,
            ConversationMemoryPolicyResolver.MODE_SUMMARY_AND_FACTS
    );

    private final ObjectMapper objectMapper;

    public ConversationPolicyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 创建会话时合并 Agent 默认策略和会话覆盖项。 */
    public String buildMetadata(AgentSnapshot agentSnapshot, CreateConversationRequest request) {
        validateUserMemoryMode(request.userMemoryMode());
        JsonNode agentMetadata = agentSnapshot == null ? null : agentSnapshot.metadata();
        ConversationMemoryPolicyResolver.ConversationUserMemoryPolicy agentDefaults =
                ConversationMemoryPolicyResolver.resolveAgentDefaultPolicy(agentMetadata);
        JsonNode policyNode = ConversationMemoryPolicyResolver.applyConversationPolicy(
                objectMapper, null, agentDefaults, request.userMemoryEnabled(),
                request.userMemoryMode(), request.userMemoryWritebackEnabled());
        ObjectNode root = mutableObject(policyNode);
        persistAgentDefaults(root, agentMetadata);
        applyCompactionPolicy(root, request.preferSummaryOverRawHistory(), request.maxRecentMessages(),
                request.maxRecentUserMessages(), request.preferStateFirst(),
                request.historySummaryThresholdMessages(), request.minRetentionRatio(),
                request.qualityWarningThreshold());
        return root.toString();
    }

    /** 更新会话时只覆盖请求明确提供的策略字段。 */
    public String mergeMetadata(JsonNode currentMetadata, AgentSnapshot agentSnapshot,
                                UpdateConversationMemoryPolicyRequest request) {
        validateUserMemoryMode(request.userMemoryMode());
        JsonNode agentMetadata = agentSnapshot == null ? null : agentSnapshot.metadata();
        ConversationMemoryPolicyResolver.ConversationUserMemoryPolicy agentDefaults =
                ConversationMemoryPolicyResolver.resolveAgentDefaultPolicy(agentMetadata);
        JsonNode policyNode = ConversationMemoryPolicyResolver.applyConversationPolicy(
                objectMapper, currentMetadata, agentDefaults, request.userMemoryEnabled(),
                request.userMemoryMode(), request.userMemoryWritebackEnabled());
        ObjectNode root = mutableObject(policyNode);
        applyCompactionPolicy(root, request.preferSummaryOverRawHistory(), request.maxRecentMessages(),
                request.maxRecentUserMessages(), request.preferStateFirst(),
                request.historySummaryThresholdMessages(), request.minRetentionRatio(),
                request.qualityWarningThreshold());
        return root.toString();
    }

    /** 解析会话生效策略，会话配置优先于持久化的 Agent 默认策略快照。 */
    public PolicySnapshot resolveEffective(JsonNode conversationMetadata) {
        PolicySnapshot defaults = resolveAgentDefault(conversationMetadata);
        JsonNode node = conversationMetadata == null ? null : conversationMetadata.get("compactionPolicy");
        if (node == null || !node.isObject()) return defaults;
        return readSnapshot(node, defaults);
    }

    /** 解析创建会话时固化的 Agent 默认策略快照。 */
    public PolicySnapshot resolveAgentDefault(JsonNode metadata) {
        JsonNode source = metadata;
        if (source != null && source.has("agentDefaultCompactionPolicy")) source = source.get("agentDefaultCompactionPolicy");
        else if (source != null && source.has("compactionPolicy")) source = source.get("compactionPolicy");
        return readSnapshot(source, PolicySnapshot.defaults());
    }

    /** 更新只属于当前会话的交互偏好。 */
    public String mergeInteractionPreference(JsonNode currentMetadata, boolean responseStreamingEnabled) {
        ObjectNode root = mutableObject(currentMetadata);
        root.with("interactionPolicy").put("responseStreamingEnabled", responseStreamingEnabled);
        return root.toString();
    }

    /** 解析当前会话是否启用流式响应，未配置时默认关闭。 */
    public boolean resolveResponseStreamingEnabled(JsonNode metadata) {
        JsonNode interactionPolicy = metadata == null ? null : metadata.get("interactionPolicy");
        return bool(interactionPolicy, "responseStreamingEnabled", false);
    }

    private ObjectNode mutableObject(JsonNode node) {
        return node != null && node.isObject() ? ((ObjectNode) node).deepCopy() : objectMapper.createObjectNode();
    }

    private void validateUserMemoryMode(String mode) {
        if (mode != null && !mode.isBlank() && !ALLOWED_USER_MEMORY_MODES.contains(mode)) {
            throw new BusinessException("用户历史记忆模式不合法");
        }
    }

    private void persistAgentDefaults(ObjectNode root, JsonNode agentMetadata) {
        PolicySnapshot snapshot = resolveAgentDefault(agentMetadata);
        ObjectNode node = root.with("agentDefaultCompactionPolicy");
        node.put("enabled", true);
        writeSnapshot(node, snapshot);
    }

    private void applyCompactionPolicy(ObjectNode root, Boolean preferSummary, Integer maxMessages,
                                       Integer maxUserMessages, Boolean preferState, Integer summaryThreshold,
                                       Double retentionRatio, Integer qualityThreshold) {
        ObjectNode node = root.with("compactionPolicy");
        node.put("enabled", true);
        if (preferSummary != null) node.put("preferSummaryOverRawHistory", preferSummary);
        if (maxMessages != null && maxMessages > 0) node.put("maxRecentMessages", maxMessages);
        if (maxUserMessages != null && maxUserMessages > 0) node.put("maxRecentUserMessages", maxUserMessages);
        if (preferState != null) node.put("preferStateFirst", preferState);
        if (summaryThreshold != null && summaryThreshold > 0) node.put("historySummaryThresholdMessages", summaryThreshold);
        if (retentionRatio != null && retentionRatio >= 0.1D && retentionRatio <= 0.9D) node.put("minRetentionRatio", retentionRatio);
        if (qualityThreshold != null) node.put("qualityWarningThreshold", Math.max(1, Math.min(100, qualityThreshold)));
    }

    private PolicySnapshot readSnapshot(JsonNode node, PolicySnapshot defaults) {
        return new PolicySnapshot(bool(node, "preferSummaryOverRawHistory", defaults.preferSummaryOverRawHistory()),
                positive(node, "maxRecentMessages", defaults.maxRecentMessages()),
                positive(node, "maxRecentUserMessages", defaults.maxRecentUserMessages()),
                bool(node, "preferStateFirst", defaults.preferStateFirst()),
                positive(node, "historySummaryThresholdMessages", defaults.historySummaryThresholdMessages()),
                ratio(node, "minRetentionRatio", defaults.minRetentionRatio()),
                bounded(node, "qualityWarningThreshold", defaults.qualityWarningThreshold(), 1, 100));
    }

    private void writeSnapshot(ObjectNode node, PolicySnapshot value) {
        node.put("preferSummaryOverRawHistory", value.preferSummaryOverRawHistory());
        node.put("maxRecentMessages", value.maxRecentMessages());
        node.put("maxRecentUserMessages", value.maxRecentUserMessages());
        node.put("preferStateFirst", value.preferStateFirst());
        node.put("historySummaryThresholdMessages", value.historySummaryThresholdMessages());
        node.put("minRetentionRatio", value.minRetentionRatio());
        node.put("qualityWarningThreshold", value.qualityWarningThreshold());
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isBoolean() ? value.booleanValue() : fallback;
    }

    private int positive(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() && value.intValue() > 0 ? value.intValue() : fallback;
    }

    private int bounded(JsonNode node, String field, int fallback, int min, int max) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() ? Math.max(min, Math.min(max, value.intValue())) : fallback;
    }

    private double ratio(JsonNode node, String field, double fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isNumber()) return fallback;
        double number = value.doubleValue();
        return number >= 0.1D && number <= 0.9D ? number : fallback;
    }

    public record PolicySnapshot(boolean preferSummaryOverRawHistory, int maxRecentMessages,
                                 int maxRecentUserMessages, boolean preferStateFirst,
                                 int historySummaryThresholdMessages, double minRetentionRatio,
                                 int qualityWarningThreshold) {
        public static PolicySnapshot defaults() {
            return new PolicySnapshot(true, 6, 4, true, 6, 0.18D, 75);
        }
    }
}
