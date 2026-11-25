package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;

/**
 * 会话用户记忆策略解析器。
 * 统一处理 Agent 默认配置、会话覆盖配置以及运行时有效策略。
 */
public final class ConversationMemoryPolicyResolver {

    public static final String MODE_OFF = "off";
    public static final String MODE_SUMMARY_ONLY = "summary_only";
    public static final String MODE_SUMMARY_AND_FACTS = "summary_and_facts";
    public static final boolean DEFAULT_USER_MEMORY_ENABLED = true;
    public static final String DEFAULT_USER_MEMORY_MODE = MODE_SUMMARY_ONLY;
    public static final boolean DEFAULT_USER_MEMORY_WRITEBACK_ENABLED = true;

    private ConversationMemoryPolicyResolver() {
    }

    public static ConversationUserMemoryPolicy resolveAgentDefaultPolicy(JsonNode agentMetadata) {
        JsonNode memoryPolicy = objectNode(agentMetadata == null ? null : agentMetadata.get("memoryPolicy"));
        boolean enabled = asBoolean(memoryPolicy.get("userMemoryEnabled"), DEFAULT_USER_MEMORY_ENABLED);
        String mode = normalizeMode(asText(memoryPolicy.get("userMemoryMode")), DEFAULT_USER_MEMORY_MODE);
        boolean writebackEnabled = asBoolean(memoryPolicy.get("userMemoryWritebackEnabled"), DEFAULT_USER_MEMORY_WRITEBACK_ENABLED);
        return new ConversationUserMemoryPolicy(enabled, mode, writebackEnabled, enabled, mode, writebackEnabled);
    }

    public static ConversationUserMemoryPolicy resolveConversationPolicy(JsonNode conversationMetadata,
                                                                         JsonNode agentMetadata) {
        ConversationUserMemoryPolicy defaults = resolveAgentDefaultPolicy(agentMetadata);
        JsonNode memoryPolicy = objectNode(conversationMetadata == null ? null : conversationMetadata.get("memoryPolicy"));
        boolean agentDefaultEnabled = asBoolean(memoryPolicy.get("agentDefaultUserMemoryEnabled"), defaults.agentDefaultUserMemoryEnabled());
        String agentDefaultMode = normalizeMode(asText(memoryPolicy.get("agentDefaultUserMemoryMode")), defaults.agentDefaultUserMemoryMode());
        boolean agentDefaultWriteback = asBoolean(memoryPolicy.get("agentDefaultUserMemoryWritebackEnabled"), defaults.agentDefaultUserMemoryWritebackEnabled());
        boolean enabled = asBoolean(memoryPolicy.get("userMemoryEnabled"), agentDefaultEnabled);
        String mode = normalizeMode(asText(memoryPolicy.get("userMemoryMode")), agentDefaultMode);
        boolean writebackEnabled = asBoolean(memoryPolicy.get("userMemoryWritebackEnabled"), agentDefaultWriteback);
        return new ConversationUserMemoryPolicy(
                enabled,
                mode,
                writebackEnabled,
                agentDefaultEnabled,
                agentDefaultMode,
                agentDefaultWriteback
        );
    }

    public static ObjectNode applyConversationPolicy(ObjectMapper objectMapper,
                                                     JsonNode conversationMetadata,
                                                     ConversationUserMemoryPolicy agentDefaults,
                                                     Boolean userMemoryEnabled,
                                                     String userMemoryMode,
                                                     Boolean userMemoryWritebackEnabled) {
        ObjectNode root = conversationMetadata != null && conversationMetadata.isObject()
                ? conversationMetadata.deepCopy()
                : objectMapper.createObjectNode();
        ObjectNode memoryPolicy = root.with("memoryPolicy");
        memoryPolicy.put("agentDefaultUserMemoryEnabled", agentDefaults.agentDefaultUserMemoryEnabled());
        memoryPolicy.put("agentDefaultUserMemoryMode", agentDefaults.agentDefaultUserMemoryMode());
        memoryPolicy.put("agentDefaultUserMemoryWritebackEnabled", agentDefaults.agentDefaultUserMemoryWritebackEnabled());
        memoryPolicy.put("userMemoryEnabled", userMemoryEnabled == null ? agentDefaults.userMemoryEnabled() : userMemoryEnabled);
        memoryPolicy.put("userMemoryMode", normalizeMode(userMemoryMode, agentDefaults.userMemoryMode()));
        memoryPolicy.put("userMemoryWritebackEnabled", userMemoryWritebackEnabled == null
                ? agentDefaults.userMemoryWritebackEnabled()
                : userMemoryWritebackEnabled);
        return root;
    }

    public static ExecutionContext.UserMemoryContext applyMode(ExecutionContext.UserMemoryContext context, String mode) {
        if (context == null) {
            return null;
        }
        String normalizedMode = normalizeMode(mode, DEFAULT_USER_MEMORY_MODE);
        if (MODE_OFF.equals(normalizedMode)) {
            return null;
        }
        if (MODE_SUMMARY_ONLY.equals(normalizedMode)) {
            return new ExecutionContext.UserMemoryContext(
                    context.preferenceSummary(),
                    context.attentionSummary(),
                    null,
                    context.goalSummary()
            );
        }
        return context;
    }

    public static String normalizeMode(String value, String defaultValue) {
        if (MODE_OFF.equals(value) || MODE_SUMMARY_ONLY.equals(value) || MODE_SUMMARY_AND_FACTS.equals(value)) {
            return value;
        }
        return defaultValue == null || defaultValue.isBlank() ? DEFAULT_USER_MEMORY_MODE : defaultValue;
    }

    private static JsonNode objectNode(JsonNode node) {
        // 缺少策略对象时仍允许调用方安全读取字段，并逐项回退到平台默认值。
        return node != null && node.isObject() ? node : MissingNode.getInstance();
    }

    private static boolean asBoolean(JsonNode node, boolean defaultValue) {
        return node != null && node.isBoolean() ? node.booleanValue() : defaultValue;
    }

    private static String asText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    public record ConversationUserMemoryPolicy(
            boolean userMemoryEnabled,
            String userMemoryMode,
            boolean userMemoryWritebackEnabled,
            boolean agentDefaultUserMemoryEnabled,
            String agentDefaultUserMemoryMode,
            boolean agentDefaultUserMemoryWritebackEnabled
    ) {
    }
}
