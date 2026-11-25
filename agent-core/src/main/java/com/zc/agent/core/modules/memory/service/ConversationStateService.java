package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用会话状态中心。
 * 负责解析 Agent 配置的 dialogState schema，并把会话元数据维护为统一状态对象。
 */
@Service
public class ConversationStateService {

    private final ObjectMapper objectMapper;
    private final ConversationSlotExtractionService slotExtractionService;

    public ConversationStateService(ObjectMapper objectMapper,
                                    ConversationSlotExtractionService slotExtractionService) {
        this.objectMapper = objectMapper;
        this.slotExtractionService = slotExtractionService;
    }

    /**
     * 解析结构化会话状态。
     */
    public ExecutionContext.ConversationStateContext resolveConversationStateContext(JsonNode agentMetadata,
                                                                                     JsonNode conversationMetadataJson) {
        List<ExecutionContext.StateSlotSchemaItem> schemaItems = resolveStateSchema(agentMetadata);
        JsonNode dialogState = resolveDialogState(conversationMetadataJson);
        if ((dialogState == null || dialogState.isMissingNode() || dialogState.isNull()) && schemaItems.isEmpty()) {
            return null;
        }
        return buildConversationStateContext(schemaItems, dialogState);
    }

    /**
     * 根据本轮执行结果更新会话状态元数据。
     */
    public String updateConversationMetadata(JsonNode agentMetadata,
                                             String existingMetadataJson,
                                             ExecutionContext executionContext,
                                             String assistantContent) {
        ObjectNode metadataNode = parseObject(existingMetadataJson);
        List<ExecutionContext.StateSlotSchemaItem> schemaItems = resolveStateSchema(agentMetadata);
        if (schemaItems.isEmpty()) {
            return toJsonText(metadataNode);
        }
        ObjectNode dialogStateNode = resolveObjectNode(metadataNode.path("dialogState"));
        ObjectNode confirmedSlotsNode = resolveObjectNode(dialogStateNode.path("confirmedSlots"));
        ArrayNode missingSlotsNode = objectMapper.createArrayNode();
        ArrayNode conflictsNode = objectMapper.createArrayNode();

        String historyText = safeText(executionContext.recentConversationText());
        String currentUserMessage = safeText(executionContext.userMessage());
        String currentAssistantMessage = safeText(assistantContent);
        String now = LocalDateTime.now().toString();
        Map<String, String> llmExtractedSlots = slotExtractionService.extract(
                executionContext,
                schemaItems,
                currentUserMessage,
                historyText,
                currentAssistantMessage
        );

        for (ExecutionContext.StateSlotSchemaItem schemaItem : schemaItems) {
            String key = schemaItem.key();
            if (isBlank(key)) {
                continue;
            }
            ObjectNode existingSlot = resolveObjectNode(confirmedSlotsNode.path(key));
            String existingValue = existingSlot.path("value").asText("").trim();
            String detectedValue = firstNonBlank(
                    llmExtractedSlots.get(key),
                    detectSlotValue(schemaItem, currentUserMessage, historyText)
            );
            if (isBlank(detectedValue)) {
                if (!isBlank(existingValue)) {
                    confirmedSlotsNode.set(key, enrichSlot(existingSlot, schemaItem, existingValue, "existing_state", now));
                } else if (schemaItem.required()) {
                    missingSlotsNode.add(buildMissingSlotNode(schemaItem, "未从当前会话中识别到值"));
                }
                continue;
            }
            if (isBlank(existingValue)) {
                confirmedSlotsNode.set(key, buildSlotNode(schemaItem, detectedValue, "current_turn", now));
                continue;
            }
            if (Objects.equals(normalizeSlotValue(existingValue), normalizeSlotValue(detectedValue))) {
                confirmedSlotsNode.set(key, enrichSlot(existingSlot, schemaItem, existingValue, "existing_state", now));
                continue;
            }
            String overwritePolicy = lowerText(schemaItem.overwritePolicy());
            if ("latest".equals(overwritePolicy) || "replace".equals(overwritePolicy)) {
                conflictsNode.add(buildConflictNode(schemaItem, existingValue, detectedValue, "replace", "按 overwritePolicy 覆盖旧值"));
                confirmedSlotsNode.set(key, buildSlotNode(schemaItem, detectedValue, "current_turn", now));
            } else {
                conflictsNode.add(buildConflictNode(schemaItem, existingValue, detectedValue, "keep_existing", "检测到新旧值冲突，保留已确认值"));
                confirmedSlotsNode.set(key, enrichSlot(existingSlot, schemaItem, existingValue, "existing_state", now));
            }
        }

        ObjectNode stateViewNode = buildStateViewNode(schemaItems, confirmedSlotsNode, missingSlotsNode, conflictsNode, dialogStateNode.path("summary").asText(null));
        dialogStateNode.set("confirmedSlots", confirmedSlotsNode);
        dialogStateNode.set("missingSlots", missingSlotsNode);
        dialogStateNode.set("conflicts", conflictsNode);
        dialogStateNode.set("stateView", stateViewNode);
        dialogStateNode.put("summary", stateViewNode.path("summary").asText(""));
        dialogStateNode.put("updatedAt", now);
        dialogStateNode.put("enabled", true);
        metadataNode.set("dialogState", dialogStateNode);
        return toJsonText(metadataNode);
    }

    private ExecutionContext.ConversationStateContext buildConversationStateContext(List<ExecutionContext.StateSlotSchemaItem> schemaItems,
                                                                                    JsonNode dialogStateNode) {
        boolean enabled = dialogStateNode == null || dialogStateNode.path("enabled").asBoolean(true);
        String updatedAt = dialogStateNode == null ? null : dialogStateNode.path("updatedAt").asText(null);
        List<ExecutionContext.StateSlotValue> confirmedSlots = resolveConfirmedSlots(schemaItems, dialogStateNode);
        List<ExecutionContext.StateMissingSlot> missingSlots = resolveMissingSlots(schemaItems, dialogStateNode, confirmedSlots);
        List<ExecutionContext.StateConflictItem> conflicts = resolveConflicts(dialogStateNode);
        List<String> confirmedFacts = resolveStringList(dialogStateNode, "stateView", "confirmedFacts");
        List<String> preferences = resolveStringList(dialogStateNode, "stateView", "preferences");
        List<String> constraints = resolveStringList(dialogStateNode, "stateView", "constraints");
        List<String> goals = resolveStringList(dialogStateNode, "stateView", "goals");
        List<String> openQuestions = resolveStringList(dialogStateNode, "stateView", "openQuestions");
        if (confirmedFacts.isEmpty()) {
            confirmedFacts = confirmedSlots.stream()
                    .map(item -> item.label() + "=" + item.value())
                    .toList();
        }
        if (openQuestions.isEmpty()) {
            openQuestions = missingSlots.stream()
                    .map(ExecutionContext.StateMissingSlot::label)
                    .filter(label -> label != null && !label.isBlank())
                    .toList();
        }
        String summary = firstNonBlank(
                dialogStateNode == null ? null : dialogStateNode.path("summary").asText(null),
                buildStateSummary(confirmedSlots, missingSlots, conflicts)
        );
        String promptText = buildPromptText(summary, confirmedFacts, preferences, constraints, goals, openQuestions, conflicts);
        if (isBlank(summary) && confirmedSlots.isEmpty() && missingSlots.isEmpty() && conflicts.isEmpty()) {
            return null;
        }
        return new ExecutionContext.ConversationStateContext(
                enabled,
                summary,
                promptText,
                updatedAt,
                schemaItems,
                confirmedSlots,
                missingSlots,
                confirmedFacts,
                preferences,
                constraints,
                goals,
                openQuestions,
                conflicts
        );
    }

    private List<ExecutionContext.StateSlotSchemaItem> resolveStateSchema(JsonNode agentMetadata) {
        JsonNode slotSchemaNode = resolveSlotSchema(agentMetadata);
        if (slotSchemaNode == null || !slotSchemaNode.isArray() || slotSchemaNode.isEmpty()) {
            return List.of();
        }
        List<ExecutionContext.StateSlotSchemaItem> items = new ArrayList<>();
        for (JsonNode slotNode : slotSchemaNode) {
            String key = slotNode.path("key").asText("").trim();
            if (key.isEmpty()) {
                continue;
            }
            List<String> enumValues = new ArrayList<>();
            JsonNode enumNode = slotNode.path("enumValues");
            if (!enumNode.isArray()) {
                enumNode = slotNode.path("enum");
            }
            if (enumNode.isArray()) {
                for (JsonNode item : enumNode) {
                    String value = item.asText("").trim();
                    if (!value.isEmpty()) {
                        enumValues.add(value);
                    }
                }
            }
            items.add(new ExecutionContext.StateSlotSchemaItem(
                    key,
                    firstNonBlank(slotNode.path("label").asText(null), key),
                    firstNonBlank(slotNode.path("type").asText(null), "string"),
                    slotNode.path("required").asBoolean(true),
                    slotNode.path("multiValue").asBoolean(false),
                    enumValues,
                    resolveStringArray(slotNode.path("aliases")),
                    slotNode.path("extractionPattern").asText(null),
                    slotNode.hasNonNull("extractionGroup") ? slotNode.path("extractionGroup").asInt(1) : 1,
                    slotNode.path("description").asText(null),
                    firstNonBlank(slotNode.path("overwritePolicy").asText(null), "keep_existing"),
                    firstNonBlank(slotNode.path("conflictPolicy").asText(null), "mark_conflict")
            ));
        }
        return items;
    }

    private List<ExecutionContext.StateSlotValue> resolveConfirmedSlots(List<ExecutionContext.StateSlotSchemaItem> schemaItems,
                                                                        JsonNode dialogStateNode) {
        Map<String, ExecutionContext.StateSlotSchemaItem> schemaMap = new LinkedHashMap<>();
        for (ExecutionContext.StateSlotSchemaItem item : schemaItems) {
            schemaMap.put(item.key(), item);
        }
        JsonNode confirmedSlotsNode = dialogStateNode == null ? null : dialogStateNode.path("confirmedSlots");
        if (confirmedSlotsNode == null || !confirmedSlotsNode.isObject()) {
            return List.of();
        }
        List<ExecutionContext.StateSlotValue> values = new ArrayList<>();
        confirmedSlotsNode.fieldNames().forEachRemaining(key -> {
            JsonNode slotNode = confirmedSlotsNode.path(key);
            String value = slotNode.path("value").asText("").trim();
            if (value.isBlank()) {
                return;
            }
            ExecutionContext.StateSlotSchemaItem schemaItem = schemaMap.get(key);
            values.add(new ExecutionContext.StateSlotValue(
                    key,
                    firstNonBlank(slotNode.path("label").asText(null), schemaItem == null ? key : schemaItem.label()),
                    value,
                    schemaItem == null ? "string" : schemaItem.type(),
                    schemaItem != null && schemaItem.required(),
                    schemaItem != null && schemaItem.multiValue(),
                    slotNode.hasNonNull("confidence") ? slotNode.path("confidence").asDouble(1.0d) : 1.0d,
                    firstNonBlank(slotNode.path("source").asText(null), "existing_state"),
                    slotNode.path("updatedAt").asText(dialogStateNode.path("updatedAt").asText(null))
            ));
        });
        return values;
    }

    private List<ExecutionContext.StateMissingSlot> resolveMissingSlots(List<ExecutionContext.StateSlotSchemaItem> schemaItems,
                                                                        JsonNode dialogStateNode,
                                                                        List<ExecutionContext.StateSlotValue> confirmedSlots) {
        Set<String> confirmedKeys = new LinkedHashSet<>();
        for (ExecutionContext.StateSlotValue confirmedSlot : confirmedSlots) {
            confirmedKeys.add(confirmedSlot.key());
        }
        Map<String, ExecutionContext.StateMissingSlot> missingMap = new LinkedHashMap<>();
        JsonNode missingSlotsNode = dialogStateNode == null ? null : dialogStateNode.path("missingSlots");
        if (missingSlotsNode != null && missingSlotsNode.isArray()) {
            for (JsonNode item : missingSlotsNode) {
                String key = item.path("key").asText("").trim();
                if (key.isEmpty() || confirmedKeys.contains(key)) {
                    continue;
                }
                missingMap.put(key, new ExecutionContext.StateMissingSlot(
                        key,
                        firstNonBlank(item.path("label").asText(null), key),
                        item.path("required").asBoolean(true),
                        firstNonBlank(item.path("reason").asText(null), "缺少关键信息")
                ));
            }
        }
        for (ExecutionContext.StateSlotSchemaItem schemaItem : schemaItems) {
            if (schemaItem.required() && !confirmedKeys.contains(schemaItem.key()) && !missingMap.containsKey(schemaItem.key())) {
                missingMap.put(schemaItem.key(), new ExecutionContext.StateMissingSlot(
                        schemaItem.key(),
                        schemaItem.label(),
                        true,
                        "缺少关键信息"
                ));
            }
        }
        return new ArrayList<>(missingMap.values());
    }

    private List<ExecutionContext.StateConflictItem> resolveConflicts(JsonNode dialogStateNode) {
        JsonNode conflictsNode = dialogStateNode == null ? null : dialogStateNode.path("conflicts");
        if (conflictsNode == null || !conflictsNode.isArray()) {
            return List.of();
        }
        List<ExecutionContext.StateConflictItem> items = new ArrayList<>();
        for (JsonNode item : conflictsNode) {
            items.add(new ExecutionContext.StateConflictItem(
                    item.path("key").asText(""),
                    firstNonBlank(item.path("label").asText(null), item.path("key").asText(null)),
                    item.path("existingValue").asText(""),
                    item.path("newValue").asText(""),
                    item.path("resolution").asText(""),
                    item.path("reason").asText("")
            ));
        }
        return items;
    }

    private ObjectNode buildStateViewNode(List<ExecutionContext.StateSlotSchemaItem> schemaItems,
                                          ObjectNode confirmedSlotsNode,
                                          ArrayNode missingSlotsNode,
                                          ArrayNode conflictsNode,
                                          String fallbackSummary) {
        List<ExecutionContext.StateSlotValue> confirmedSlots = new ArrayList<>();
        confirmedSlotsNode.fieldNames().forEachRemaining(key -> {
            JsonNode slotNode = confirmedSlotsNode.path(key);
            String value = slotNode.path("value").asText("").trim();
            if (!value.isBlank()) {
                ExecutionContext.StateSlotSchemaItem schemaItem = schemaItems.stream()
                        .filter(item -> key.equals(item.key()))
                        .findFirst()
                        .orElse(null);
                confirmedSlots.add(new ExecutionContext.StateSlotValue(
                        key,
                        firstNonBlank(slotNode.path("label").asText(null), schemaItem == null ? key : schemaItem.label()),
                        value,
                        schemaItem == null ? "string" : schemaItem.type(),
                        schemaItem != null && schemaItem.required(),
                        schemaItem != null && schemaItem.multiValue(),
                        slotNode.hasNonNull("confidence") ? slotNode.path("confidence").asDouble(1.0d) : 1.0d,
                        slotNode.path("source").asText("current_turn"),
                        slotNode.path("updatedAt").asText(null)
                ));
            }
        });
        List<ExecutionContext.StateMissingSlot> missingSlots = new ArrayList<>();
        for (JsonNode item : missingSlotsNode) {
            missingSlots.add(new ExecutionContext.StateMissingSlot(
                    item.path("key").asText(""),
                    firstNonBlank(item.path("label").asText(null), item.path("key").asText(null)),
                    item.path("required").asBoolean(true),
                    firstNonBlank(item.path("reason").asText(null), "缺少关键信息")
            ));
        }
        List<ExecutionContext.StateConflictItem> conflicts = new ArrayList<>();
        for (JsonNode item : conflictsNode) {
            conflicts.add(new ExecutionContext.StateConflictItem(
                    item.path("key").asText(""),
                    firstNonBlank(item.path("label").asText(null), item.path("key").asText(null)),
                    item.path("existingValue").asText(""),
                    item.path("newValue").asText(""),
                    item.path("resolution").asText(""),
                    item.path("reason").asText("")
            ));
        }
        List<String> confirmedFacts = confirmedSlots.stream()
                .map(item -> item.label() + "=" + item.value())
                .toList();
        List<String> preferences = extractCategoryValues(confirmedSlots, "preference", "priority", "偏好", "诉求");
        List<String> constraints = new ArrayList<>(extractCategoryValues(confirmedSlots, "constraint", "limit", "限制", "约束"));
        if (!conflicts.isEmpty()) {
            constraints.add("存在 " + conflicts.size() + " 个状态冲突待人工确认");
        }
        List<String> goals = extractCategoryValues(confirmedSlots, "goal", "目标");
        List<String> openQuestions = missingSlots.stream().map(ExecutionContext.StateMissingSlot::label).toList();
        String summary = firstNonBlank(fallbackSummary, buildStateSummary(confirmedSlots, missingSlots, conflicts));

        ObjectNode stateViewNode = objectMapper.createObjectNode();
        stateViewNode.put("summary", summary == null ? "" : summary);
        stateViewNode.set("confirmedFacts", toTextArray(confirmedFacts));
        stateViewNode.set("preferences", toTextArray(preferences));
        stateViewNode.set("constraints", toTextArray(constraints));
        stateViewNode.set("goals", toTextArray(goals));
        stateViewNode.set("openQuestions", toTextArray(openQuestions));
        return stateViewNode;
    }

    private List<String> extractCategoryValues(List<ExecutionContext.StateSlotValue> confirmedSlots, String... hints) {
        List<String> values = new ArrayList<>();
        for (ExecutionContext.StateSlotValue slot : confirmedSlots) {
            String keyText = lowerText(slot.key()) + " " + lowerText(slot.label());
            if (containsAny(keyText, hints)) {
                values.add(slot.label() + "=" + slot.value());
            }
        }
        return values;
    }

    private String buildStateSummary(List<ExecutionContext.StateSlotValue> confirmedSlots,
                                     List<ExecutionContext.StateMissingSlot> missingSlots,
                                     List<ExecutionContext.StateConflictItem> conflicts) {
        List<String> confirmedItems = confirmedSlots.stream()
                .map(item -> item.label() + "=" + item.value())
                .toList();
        List<String> missingItems = missingSlots.stream()
                .map(ExecutionContext.StateMissingSlot::label)
                .filter(label -> label != null && !label.isBlank())
                .toList();
        List<String> conflictItems = conflicts.stream()
                .map(item -> item.label() + "(" + item.existingValue() + " -> " + item.newValue() + ")")
                .toList();
        StringBuilder builder = new StringBuilder();
        if (!confirmedItems.isEmpty()) {
            builder.append("已确认：").append(String.join("；", deduplicate(confirmedItems)));
        }
        if (!missingItems.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("待补充：").append(String.join("；", deduplicate(missingItems)));
        }
        if (!conflictItems.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("待确认冲突：").append(String.join("；", deduplicate(conflictItems)));
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String buildPromptText(String summary,
                                   List<String> confirmedFacts,
                                   List<String> preferences,
                                   List<String> constraints,
                                   List<String> goals,
                                   List<String> openQuestions,
                                   List<ExecutionContext.StateConflictItem> conflicts) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(summary)) {
            builder.append(summary.trim());
        }
        appendSection(builder, "已确认事实", confirmedFacts);
        appendSection(builder, "偏好", preferences);
        appendSection(builder, "约束", constraints);
        appendSection(builder, "目标", goals);
        appendSection(builder, "待确认问题", openQuestions);
        if (!conflicts.isEmpty()) {
            List<String> conflictTexts = conflicts.stream()
                    .map(item -> item.label() + "：旧值=" + item.existingValue() + "，新值=" + item.newValue())
                    .toList();
            appendSection(builder, "冲突", conflictTexts);
        }
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String title, List<String> values) {
        List<String> normalizedValues = values == null ? List.of() : values.stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (normalizedValues.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(title).append("：").append(String.join("；", normalizedValues));
    }

    private String detectSlotValue(ExecutionContext.StateSlotSchemaItem schemaItem,
                                   String currentUserMessage,
                                   String historyText) {
        String combinedText = safeText(currentUserMessage) + "\n" + safeText(historyText);
        if (!schemaItem.enumValues().isEmpty()) {
            for (String enumValue : schemaItem.enumValues()) {
                if (combinedText.contains(enumValue)) {
                    return enumValue;
                }
            }
        }
        String configuredValue = detectConfiguredPattern(schemaItem, combinedText);
        if (!configuredValue.isBlank()) {
            return configuredValue;
        }
        List<String> aliases = new ArrayList<>();
        aliases.add(schemaItem.key());
        aliases.add(schemaItem.label());
        if (schemaItem.aliases() != null) aliases.addAll(schemaItem.aliases());
        aliases = aliases.stream().filter(item -> item != null && !item.isBlank()).toList();
        if (aliases.isEmpty()) {
            return "";
        }
        String aliasPattern = aliases.stream().map(Pattern::quote).reduce((left, right) -> left + "|" + right).orElse("");
        String type = lowerText(schemaItem.type());
        if ("boolean".equals(type)) {
            return detectBooleanValue(combinedText, aliasPattern);
        }
        if (containsAny(type, "integer", "number", "decimal", "long", "double")) {
            return detectGenericValue(combinedText, aliasPattern, "[-+]?\\d+(?:\\.\\d+)?");
        }
        if (containsAny(type, "date", "datetime", "time")) {
            return detectGenericValue(combinedText, aliasPattern, "\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)?");
        }
        return detectGenericValue(combinedText, aliasPattern, "[^,，。;；\\n]{1,200}");
    }

    private String detectConfiguredPattern(ExecutionContext.StateSlotSchemaItem schemaItem, String text) {
        if (schemaItem.extractionPattern() == null || schemaItem.extractionPattern().isBlank()) {
            return "";
        }
        try {
            var matcher = Pattern.compile(schemaItem.extractionPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            int group = schemaItem.extractionGroup() == null ? 1 : schemaItem.extractionGroup();
            return matcher.find() && group <= matcher.groupCount() ? safeText(matcher.group(group)).trim() : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private String detectGenericValue(String text, String aliasPattern, String valuePattern) {
        Pattern pattern = Pattern.compile("(?:" + aliasPattern + ")\\s*(?:是|为|=|:|：|取值为)\\s*(" + valuePattern + ")",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String detectBooleanValue(String text, String aliasPattern) {
        String value = detectGenericValue(text, aliasPattern, "true|false|是|否|开启|关闭|启用|停用");
        if (containsAny(lowerText(value), "true", "是", "开启", "启用")) return "true";
        if (containsAny(lowerText(value), "false", "否", "关闭", "停用")) return "false";
        return "";
    }

    private List<String> resolveStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) values.add(value);
        });
        return values;
    }

    private JsonNode resolveDialogState(JsonNode conversationMetadataJson) {
        if (conversationMetadataJson == null || conversationMetadataJson.isNull() || conversationMetadataJson.isMissingNode()) {
            return null;
        }
        return conversationMetadataJson.path("dialogState");
    }

    private JsonNode resolveSlotSchema(JsonNode agentMetadata) {
        if (agentMetadata == null || agentMetadata.isNull() || agentMetadata.isMissingNode()) {
            return null;
        }
        JsonNode dialogStateNode = agentMetadata.path("dialogState");
        if (dialogStateNode.isObject()) {
            JsonNode enabledNode = dialogStateNode.path("enabled");
            if (enabledNode.isMissingNode() || enabledNode.asBoolean(true)) {
                JsonNode schemaNode = dialogStateNode.path("slots");
                if (schemaNode.isArray()) {
                    return schemaNode;
                }
            }
        }
        JsonNode schemaNode = agentMetadata.path("dialogStateSchema");
        if (schemaNode.isObject()) {
            JsonNode slotsNode = schemaNode.path("slots");
            if (slotsNode.isArray()) {
                return slotsNode;
            }
        }
        JsonNode directSlotsNode = agentMetadata.path("dialogStateSlots");
        return directSlotsNode.isArray() ? directSlotsNode : null;
    }

    private ArrayNode toTextArray(List<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        deduplicate(values).forEach(arrayNode::add);
        return arrayNode;
    }

    private List<String> resolveStringList(JsonNode dialogStateNode, String objectField, String arrayField) {
        if (dialogStateNode == null || dialogStateNode.isNull() || dialogStateNode.isMissingNode()) {
            return List.of();
        }
        JsonNode valuesNode = dialogStateNode.path(objectField).path(arrayField);
        if (!valuesNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : valuesNode) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return deduplicate(values);
    }

    private List<String> deduplicate(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private ObjectNode resolveObjectNode(JsonNode node) {
        if (node != null && node.isObject()) {
            return (ObjectNode) node.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private ObjectNode buildSlotNode(ExecutionContext.StateSlotSchemaItem schemaItem,
                                     String value,
                                     String source,
                                     String updatedAt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", schemaItem.key());
        node.put("label", schemaItem.label());
        node.put("value", value);
        node.put("source", source);
        node.put("updatedAt", updatedAt);
        node.put("confidence", 1.0d);
        return node;
    }

    private ObjectNode enrichSlot(ObjectNode existingSlot,
                                  ExecutionContext.StateSlotSchemaItem schemaItem,
                                  String value,
                                  String source,
                                  String updatedAt) {
        existingSlot.put("key", schemaItem.key());
        existingSlot.put("label", schemaItem.label());
        existingSlot.put("value", value);
        existingSlot.put("source", source);
        existingSlot.put("updatedAt", updatedAt);
        if (!existingSlot.hasNonNull("confidence")) {
            existingSlot.put("confidence", 1.0d);
        }
        return existingSlot;
    }

    private ObjectNode buildMissingSlotNode(ExecutionContext.StateSlotSchemaItem schemaItem, String reason) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", schemaItem.key());
        node.put("label", schemaItem.label());
        node.put("required", schemaItem.required());
        node.put("reason", reason);
        return node;
    }

    private ObjectNode buildConflictNode(ExecutionContext.StateSlotSchemaItem schemaItem,
                                         String existingValue,
                                         String newValue,
                                         String resolution,
                                         String reason) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", schemaItem.key());
        node.put("label", schemaItem.label());
        node.put("existingValue", existingValue);
        node.put("newValue", newValue);
        node.put("resolution", resolution);
        node.put("reason", reason);
        return node;
    }

    private ObjectNode parseObject(String jsonText) {
        try {
            if (jsonText == null || jsonText.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(jsonText);
            if (node.isObject()) {
                return (ObjectNode) node;
            }
            return objectMapper.createObjectNode();
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("会话状态 JSON 序列化失败", exception);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (isBlank(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (!isBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSlotValue(String value) {
        return lowerText(value).replace(" ", "");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String lowerText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        if (!isBlank(second)) {
            return second.trim();
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
