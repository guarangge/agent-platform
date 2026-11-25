package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用会话槽位 LLM 提取器。
 * 只根据 Agent 声明的 schema 提取事实，不包含任何业务主题词表。
 */
@Service
public class ConversationSlotExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSlotExtractionService.class);

    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final ObjectMapper objectMapper;

    public ConversationSlotExtractionService(RuntimeLlmGatewayService runtimeLlmGatewayService,
                                             ObjectMapper objectMapper) {
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按通用 schema 从当前轮次与历史中提取槽位。
     */
    public Map<String, String> extract(ExecutionContext executionContext,
                                       List<ExecutionContext.StateSlotSchemaItem> schemaItems,
                                       String currentUserMessage,
                                       String historyText,
                                       String assistantMessage) {
        if (executionContext == null || !executionContext.hasRunnableModel("memory") || schemaItems == null || schemaItems.isEmpty()) {
            return Map.of();
        }
        try {
            String systemPrompt = """
                    你是通用 Agent 平台的会话状态提取器。只提取用户明确提供或历史中已明确确认的事实。
                    必须严格返回 JSON：{"slots":{"slotKey":"value"}}。
                    不确定、仅由助手提问或建议产生、存在冲突且无法判断的字段不要返回。
                    当前用户消息优先于历史消息；不得补全、猜测或使用领域常识生成值。
                    """;
            String userPrompt = objectMapper.writeValueAsString(Map.of(
                    "schema", schemaItems,
                    "currentUserMessage", safe(currentUserMessage),
                    "recentConversation", safe(historyText),
                    "assistantMessageForContextOnly", safe(assistantMessage)
            ));
            String response = runtimeLlmGatewayService.chatCompletion(systemPrompt, userPrompt, executionContext.modelForStage("memory"));
            JsonNode root = objectMapper.readTree(runtimeLlmGatewayService.sanitizeJsonText(response));
            JsonNode slots = root.path("slots");
            if (!slots.isObject()) {
                return Map.of();
            }
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (ExecutionContext.StateSlotSchemaItem item : schemaItems) {
                JsonNode valueNode = slots.path(item.key());
                String value = valueNode.isValueNode() ? valueNode.asText("").trim() : "";
                if (!value.isBlank() && matchesEnum(item, value)) {
                    values.put(item.key(), value);
                }
            }
            return values;
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("通用会话槽位 LLM 提取失败，回退 schema 规则：{}", exception.getMessage());
            return Map.of();
        }
    }

    private boolean matchesEnum(ExecutionContext.StateSlotSchemaItem item, String value) {
        return item.enumValues() == null || item.enumValues().isEmpty() || item.enumValues().contains(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
