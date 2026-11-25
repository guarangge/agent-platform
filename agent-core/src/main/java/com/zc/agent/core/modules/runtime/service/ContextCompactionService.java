package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一上下文压缩服务。
 * 负责按阶段预算对多层上下文进行裁剪、摘要压缩与可观测信息生成。
 */
@Service
public class ContextCompactionService {

    private static final int DEFAULT_MAX_PREVIOUS_SUMMARIES = 4;
    private static final int MIN_SUMMARY_LENGTH = 120;
    private static final Pattern TOPIC_PATTERN = Pattern.compile("[\\p{IsHan}]{2,8}|[A-Za-z][A-Za-z0-9_-]{2,20}");
    private static final Set<String> STOP_WORDS = Set.of(
            "请问", "请帮我", "帮我", "一下", "这个", "那个", "继续", "处理", "分析", "建议", "问题",
            "当前", "我们", "你们", "还有", "已经", "需要", "可以", "怎么", "什么", "哪些"
    );

    private final ObjectMapper objectMapper;
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionPolicyResolver policyResolver;

    public ContextCompactionService(ObjectMapper objectMapper,
                                    PromptContextBudgetService promptContextBudgetService,
                                    ContextCompactionPolicyResolver policyResolver) {
        this.objectMapper = objectMapper;
        this.promptContextBudgetService = promptContextBudgetService;
        this.policyResolver = policyResolver;
    }

    public StageCompactionBundle compactStageContext(ExecutionContext executionContext,
                                                     PromptContextBudgetService.PromptStage stage) {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext == null ? null : executionContext.modelForStage(stage.name().toLowerCase()),
                stage
        );
        return compactStageContext(executionContext, budget);
    }

    /**
     * 压缩当前阶段所需的核心上下文。
     */
    public StageCompactionBundle compactStageContext(ExecutionContext executionContext,
                                                     PromptContextBudgetService.StageBudget budget) {
        ContextCompactionPolicy policy = policyResolver.resolve(executionContext);
        LinkedHashMap<String, LayerCompaction> layers = new LinkedHashMap<>();
        Map<String, Object> protectedFactsPayload = executionContext == null
                ? Map.of()
                : executionContext.protectedStateFactsPayload();
        String protectedFactsText = compactTextLayer(
                "protectedFactsText",
                executionContext == null ? null : executionContext.protectedStateFactsText(),
                Math.max(220, budget.factChars()),
                false,
                policy,
                budget,
                layers
        );
        String layeredConversationSummary = compactTextLayer(
                "layeredConversationSummary",
                executionContext == null ? null : executionContext.layeredConversationSummaryText(),
                Math.max(MIN_SUMMARY_LENGTH, budget.conversationMemoryChars() / 2),
                true,
                policy,
                budget,
                layers
        );
        String currentUserMessage = compactTextLayer(
                "currentUserMessage",
                executionContext == null ? null : executionContext.userMessage(),
                budget.userMessageChars(),
                false,
                policy,
                budget,
                layers
        );
        String conversationStateText = compactTextLayer(
                "conversationStateText",
                executionContext == null ? null : executionContext.conversationStatePromptText(),
                budget.conversationStateChars(),
                true,
                policy,
                budget,
                layers
        );
        List<ExecutionContext.ConversationMessageContext> recentMessages = executionContext == null
                ? List.of()
                : executionContext.recentConversationMessages();
        List<String> recentUserMessages = compactRecentUserMessages(
                recentMessages,
                budget.historyMessageChars(),
                policy,
                budget,
                layers
        );
        String recentConversationSummary = compactRecentConversationSummary(
                executionContext,
                recentMessages,
                budget.historyMessageChars() * 2,
                policy,
                budget,
                layers
        );
        String conversationAwareUserMessage = buildConversationAwareUserMessage(
                protectedFactsText,
                currentUserMessage,
                conversationStateText,
                recentConversationSummary,
                recentUserMessages,
                budget.promptChars(),
                policy
        );
        ValidationResult validationResult = validateAndRepairCompaction(
                conversationAwareUserMessage,
                protectedFactsPayload,
                protectedFactsText,
                currentUserMessage,
                budget,
                policy
        );
        conversationAwareUserMessage = validationResult.compactedText();
        if (validationResult.requiresRetry()) {
            ContextCompactionPolicy retryPolicy = policy.toRetryPolicy();
            conversationAwareUserMessage = buildConversationAwareUserMessage(
                    protectedFactsText,
                    currentUserMessage,
                    conversationStateText,
                    recentConversationSummary,
                    recentUserMessages,
                    budget.promptChars(),
                    retryPolicy
            );
        }
        layers.put("conversationAwareUserMessage", new LayerCompaction(
                "conversationAwareUserMessage",
                safeLength(protectedFactsText) + safeLength(currentUserMessage) + safeLength(conversationStateText) + safeLength(recentConversationSummary),
                safeLength(conversationAwareUserMessage),
                safeLength(conversationAwareUserMessage),
                estimateTokens(joinLines(protectedFactsText, currentUserMessage, conversationStateText, recentConversationSummary), budget),
                estimateTokens(conversationAwareUserMessage, budget),
                estimateTokens(conversationAwareUserMessage, budget),
                recentConversationSummary == null || recentConversationSummary.isBlank() ? "window" : "window+summary",
                recentUserMessages.size(),
                recentConversationSummary != null && !recentConversationSummary.isBlank(),
                safeLength(conversationAwareUserMessage) < safeLength(currentUserMessage) + safeLength(conversationStateText) + safeLength(recentConversationSummary),
                joinLines(protectedFactsText, currentUserMessage, conversationStateText, recentConversationSummary),
                conversationAwareUserMessage
        ));
        String conversationMemory = compactTextLayer(
                "conversationMemory",
                executionContext == null ? null : executionContext.conversationMemoryText(),
                budget.conversationMemoryChars(),
                true,
                policy,
                budget,
                layers
        );
        String userMemory = compactTextLayer(
                "userMemory",
                executionContext == null ? null : executionContext.userMemoryText(),
                budget.conversationMemoryChars(),
                true,
                policy,
                budget,
                layers
        );
        String recentTaskMemory = compactTextLayer(
                "recentTaskMemory",
                executionContext == null ? null : executionContext.recentTaskMemoryText(),
                budget.recentTaskMemoryChars(),
                true,
                policy,
                budget,
                layers
        );
        return new StageCompactionBundle(
                budget.stage().name().toLowerCase(),
                currentUserMessage,
                protectedFactsPayload,
                conversationStateText,
                conversationAwareUserMessage,
                recentConversationSummary,
                recentUserMessages,
                mergeLayeredSummaryWithText(layeredConversationSummary, conversationMemory),
                userMemory,
                recentTaskMemory,
                toMetadata(budget, layers, policy, validationResult)
        );
    }

    /**
     * 压缩前序步骤摘要。
     */
    public CompactedList compactPreviousSummaries(List<String> previousSummaries,
                                                  PromptContextBudgetService.StageBudget budget) {
        ContextCompactionPolicy policy = ContextCompactionPolicy.defaults();
        LinkedHashMap<String, LayerCompaction> layers = new LinkedHashMap<>();
        List<String> normalized = previousSummaries == null
                ? List.of()
                : previousSummaries.stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (normalized.isEmpty()) {
            return new CompactedList(List.of(), null, toMetadata(budget, layers, policy, ValidationResult.empty()));
        }
        int originalChars = normalized.stream().mapToInt(String::length).sum();
        List<String> items = new ArrayList<>();
        for (String item : normalized) {
            items.add(truncate(item.trim(), budget.previousSummaryChars()));
            if (items.size() >= DEFAULT_MAX_PREVIOUS_SUMMARIES) {
                break;
            }
        }
        String summary = null;
        if (normalized.size() > items.size() || originalChars > budget.previousSummaryChars() * DEFAULT_MAX_PREVIOUS_SUMMARIES) {
            summary = summarizeTextBlock(normalized, Math.max(MIN_SUMMARY_LENGTH, budget.factChars()));
        }
        int compactedChars = items.stream().mapToInt(String::length).sum() + safeLength(summary);
        layers.put("previousSummaries", new LayerCompaction(
                "previousSummaries",
                originalChars,
                compactedChars,
                budget.previousSummaryChars(),
                estimateTokens(String.join("\n", normalized), budget),
                estimateTokens(joinLines(items, summary), budget),
                promptContextBudgetService.estimateBudgetTokensForChars(budget.previousSummaryChars(), budget.tokenizerFamily()),
                summary == null ? "window" : "window+summary",
                items.size(),
                summary != null,
                compactedChars < originalChars,
                String.join("\n", normalized),
                joinLines(items, summary)
        ));
        return new CompactedList(items, summary, toMetadata(budget, layers, policy, ValidationResult.empty()));
    }

    /**
     * 构建滚动会话摘要，用于长会话自动压缩。
     */
    public String buildRollingConversationSummary(String existingSummary,
                                                  ExecutionContext executionContext,
                                                  String userMessage,
                                                  String assistantContent) {
        ArrayList<String> fragments = new ArrayList<>();
        if (existingSummary != null && !existingSummary.isBlank()) {
            fragments.add(truncate(existingSummary.trim(), 160));
        }
        String conversationStateSummary = executionContext == null || executionContext.conversationStateContext() == null
                ? null
                : executionContext.conversationStateContext().summary();
        if (conversationStateSummary != null && !conversationStateSummary.isBlank()) {
            fragments.add("已确认状态：" + truncate(conversationStateSummary.trim(), 120));
        }
        String topicSummary = summarizeTopics(executionContext, userMessage);
        if (!topicSummary.isBlank()) {
            fragments.add(topicSummary);
        }
        if (userMessage != null && !userMessage.isBlank()) {
            fragments.add("最近用户问题：" + truncate(userMessage.trim(), 100));
        }
        if (assistantContent != null && !assistantContent.isBlank()) {
            fragments.add("最近助手结论：" + truncate(assistantContent.replace("\n", " ").trim(), 140));
        }
        if (fragments.isEmpty()) {
            return "当前会话摘要待继续沉淀。";
        }
        return summarizeTextBlock(fragments, 320);
    }

    /**
     * 为 conversation memory metadata 补充压缩治理信息。
     */
    public String mergeConversationCompactionMetadata(String existingMetadataJson,
                                                      ExecutionContext executionContext,
                                                      String rollingSummary,
                                                      String userMessage,
                                                      String assistantContent) {
        ObjectNode root = parseObject(existingMetadataJson);
        ObjectNode contextCompaction = root.with("contextCompaction");
        ObjectNode summaryLayers = contextCompaction.with("summaryLayers");
        ObjectNode summaryLayerMetadata = contextCompaction.with("summaryLayerMetadata");
        List<ExecutionContext.ConversationMessageContext> recentMessages = executionContext == null
                ? List.of()
                : executionContext.recentConversationMessages();
        String shortTermSummary = safeText(buildShortTermSummary(executionContext, userMessage, assistantContent));
        String mediumTermSummary = safeText(buildMediumTermSummary(executionContext, rollingSummary));
        String longTermSummary = safeText(buildLongTermSummary(executionContext, rollingSummary));
        contextCompaction.put("lastCompactedAt", LocalDateTime.now().toString());
        contextCompaction.put("strategy", "rolling_summary_v1");
        contextCompaction.put("summaryLayerVersion", "v2");
        contextCompaction.put("summaryPromotionPolicy", "short=current_round, medium=rolling_window, long=protected_facts_and_goals");
        contextCompaction.put("recentMessageCount", recentMessages == null ? 0 : recentMessages.size());
        contextCompaction.put("recentUserMessageCount", countUserMessages(recentMessages));
        contextCompaction.put("rollingSummaryChars", safeLength(rollingSummary));
        contextCompaction.put("latestUserMessageChars", safeLength(userMessage));
        contextCompaction.put("latestAssistantSummaryChars", safeLength(assistantContent));
        contextCompaction.put("conversationStateEnabled",
                executionContext != null
                        && executionContext.conversationStateContext() != null
                        && executionContext.conversationStateContext().enabled());
        summaryLayers.put("shortTermSummary", shortTermSummary);
        summaryLayers.put("mediumTermSummary", mediumTermSummary);
        summaryLayers.put("longTermSummary", longTermSummary);
        fillSummaryLayerMetadata(summaryLayerMetadata.with("shortTerm"), "shortTerm", shortTermSummary, "current_round", recentMessages == null ? 0 : recentMessages.size());
        fillSummaryLayerMetadata(summaryLayerMetadata.with("mediumTerm"), "mediumTerm", mediumTermSummary, "rolling_window", recentMessages == null ? 0 : recentMessages.size());
        fillSummaryLayerMetadata(summaryLayerMetadata.with("longTerm"), "longTerm", longTermSummary, "protected_facts_and_goals",
                executionContext == null ? 0 : executionContext.protectedStateFactsPayload().size());
        contextCompaction.put("protectedFactsText", safeText(executionContext == null ? null : executionContext.protectedStateFactsText()));
        contextCompaction.set("protectedFactsPayload", objectMapper.valueToTree(executionContext == null
                ? Map.of()
                : executionContext.protectedStateFactsPayload()));
        return toJson(root);
    }

    public String explainCompaction(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "未记录压缩信息。";
        }
        Object stage = metadata.get("stage");
        Object qualityStatus = metadata.get("qualityStatus");
        Object qualityScore = metadata.get("qualityScore");
        Object savedTokens = metadata.get("savedTokens");
        Object validation = metadata.get("validation");
        return "阶段=" + stage
                + "，质量=" + qualityStatus + "(" + qualityScore + ")"
                + "，节省Token=" + savedTokens
                + "，校验=" + (validation == null ? "-" : validation);
    }

    public Map<String, Object> enrichPromptAssemblyMetadata(Map<String, Object> metadata,
                                                            String systemPrompt,
                                                            String userPrompt) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(metadata);
        String tokenizerFamily = safeObjectText(metadata.get("tokenizerFamily"));
        LinkedHashMap<String, Object> promptAssembly = metadata.get("promptAssembly") instanceof Map<?, ?> assembly
                ? new LinkedHashMap<>((Map<String, Object>) assembly)
                : new LinkedHashMap<>();
        ArrayList<Map<String, Object>> blocks = new ArrayList<>();
        Object rawBlocks = promptAssembly.get("blocks");
        if (rawBlocks instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    blocks.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        blocks.add(0, buildSyntheticPromptBlock(
                "systemPrompt",
                "System Prompt",
                "systemPrompt",
                "prompt",
                systemPrompt,
                tokenizerFamily
        ));
        promptAssembly.put("blocks", blocks);
        promptAssembly.put("blockOrder", blocks.stream().map(item -> safeObjectText(item.get("blockKey"))).toList());
        promptAssembly.put("systemPromptPreview", truncate(systemPrompt, 320));
        promptAssembly.put("finalUserPromptPreview", truncate(userPrompt, 480));
        promptAssembly.put("finalPromptPreview", truncate(joinLines(systemPrompt, userPrompt), 720));
        promptAssembly.put("estimatedSystemTokens", promptContextBudgetService.estimateTokens(systemPrompt, tokenizerFamily));
        promptAssembly.put("estimatedFinalPromptTokens", promptContextBudgetService.estimateTokens(joinLines(systemPrompt, userPrompt), tokenizerFamily));
        promptAssembly.put("diff", Map.of(
                "assembledUserPreview", safeObjectText(promptAssembly.get("preview")),
                "finalUserPromptPreview", safeText(truncate(userPrompt, 480)),
                "changed", !safeObjectText(promptAssembly.get("preview")).equals(safeText(truncate(userPrompt, 480)))
        ));
        enriched.put("promptAssembly", promptAssembly);
        enriched.put("promptSnapshot", Map.of(
                "systemPrompt", safeText(systemPrompt),
                "userPrompt", safeText(userPrompt),
                "combinedPrompt", joinLines(systemPrompt, userPrompt),
                "systemPromptChars", safeLength(systemPrompt),
                "userPromptChars", safeLength(userPrompt),
                "combinedPromptChars", safeLength(joinLines(systemPrompt, userPrompt)),
                "estimatedSystemTokens", promptContextBudgetService.estimateTokens(systemPrompt, tokenizerFamily),
                "estimatedUserTokens", promptContextBudgetService.estimateTokens(userPrompt, tokenizerFamily),
                "estimatedCombinedTokens", promptContextBudgetService.estimateTokens(joinLines(systemPrompt, userPrompt), tokenizerFamily)
        ));
        return enriched;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSyntheticPromptBlock(String blockKey,
                                                          String label,
                                                          String layerKey,
                                                          String source,
                                                          String text,
                                                          String tokenizerFamily) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("blockKey", blockKey);
        metadata.put("label", label);
        metadata.put("layerKey", layerKey);
        metadata.put("source", source);
        metadata.put("originalChars", safeLength(text));
        metadata.put("compactedChars", safeLength(text));
        metadata.put("originalTokens", promptContextBudgetService.estimateTokens(text, tokenizerFamily));
        metadata.put("compactedTokens", promptContextBudgetService.estimateTokens(text, tokenizerFamily));
        metadata.put("present", text != null && !text.isBlank());
        metadata.put("truncated", false);
        metadata.put("summarized", false);
        metadata.put("sample", truncate(text, 160));
        return metadata;
    }

    private List<String> compactRecentUserMessages(List<ExecutionContext.ConversationMessageContext> recentMessages,
                                                   int perMessageBudget,
                                                   Map<String, LayerCompaction> layers) {
        return compactRecentUserMessages(recentMessages, perMessageBudget, ContextCompactionPolicy.defaults(), null, layers);
    }

    private List<String> compactRecentUserMessages(List<ExecutionContext.ConversationMessageContext> recentMessages,
                                                   int perMessageBudget,
                                                   ContextCompactionPolicy policy,
                                                   PromptContextBudgetService.StageBudget budget,
                                                   Map<String, LayerCompaction> layers) {
        ArrayList<String> userMessages = new ArrayList<>();
        int originalChars = 0;
        if (recentMessages != null) {
            for (ExecutionContext.ConversationMessageContext message : recentMessages) {
                if (message == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                if (!"user".equalsIgnoreCase(message.messageType())) {
                    continue;
                }
                originalChars += message.content().length();
                userMessages.add(truncate(message.content().trim(), perMessageBudget));
                if (userMessages.size() >= policy.maxRecentUserMessages()) {
                    break;
                }
            }
        }
        int compactedChars = userMessages.stream().mapToInt(String::length).sum();
        layers.put("recentUserMessages", new LayerCompaction(
                "recentUserMessages",
                originalChars,
                compactedChars,
                perMessageBudget * policy.maxRecentUserMessages(),
                estimateTokensByMessages(recentMessages, "user", budget),
                estimateTokens(String.join("\n", userMessages), budget),
                promptContextBudgetService.estimateBudgetTokensForChars(perMessageBudget * policy.maxRecentUserMessages(),
                        budget == null ? null : budget.tokenizerFamily()),
                "window",
                userMessages.size(),
                false,
                compactedChars < originalChars,
                joinLines(recentMessages == null ? List.<String>of() : recentMessages.stream()
                        .filter(item -> item != null && "user".equalsIgnoreCase(item.messageType()) && item.content() != null)
                        .map(ExecutionContext.ConversationMessageContext::content)
                        .toList(), null),
                String.join("\n", userMessages)
        ));
        return userMessages;
    }

    private String compactRecentConversationSummary(ExecutionContext executionContext,
                                                    List<ExecutionContext.ConversationMessageContext> recentMessages,
                                                    int maxChars,
                                                    ContextCompactionPolicy policy,
                                                    PromptContextBudgetService.StageBudget budget,
                                                    Map<String, LayerCompaction> layers) {
        ArrayList<String> messageLines = new ArrayList<>();
        int originalChars = 0;
        if (recentMessages != null) {
            int skipCount = Math.max(0, recentMessages.size() - policy.maxRecentMessages());
            for (int index = 0; index < recentMessages.size(); index++) {
                ExecutionContext.ConversationMessageContext message = recentMessages.get(index);
                if (message == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                originalChars += message.content().length();
                if (index < skipCount) {
                    continue;
                }
                String role = "user".equalsIgnoreCase(message.messageType()) ? "用户" : "助手";
                messageLines.add(role + "：" + truncate(message.content().trim(), Math.max(40, maxChars / 4)));
            }
        }
        boolean summarized = policy.enabled()
                && policy.preferSummaryOverRawHistory()
                && (originalChars > maxChars || (recentMessages != null && recentMessages.size() > policy.historySummaryThresholdMessages()));
        String summary = summarized
                ? summarizeConversationHistory(executionContext, recentMessages, maxChars)
                : null;
        int compactedChars = safeLength(summary);
        layers.put("recentConversationSummary", new LayerCompaction(
                "recentConversationSummary",
                originalChars,
                compactedChars,
                maxChars,
                estimateTokensByMessages(recentMessages, null, budget),
                estimateTokens(summary, budget),
                promptContextBudgetService.estimateBudgetTokensForChars(maxChars, budget.tokenizerFamily()),
                summarized ? "summary" : "window",
                recentMessages == null ? 0 : recentMessages.size(),
                summarized,
                compactedChars < originalChars,
                joinLines(recentMessages == null ? List.<String>of() : recentMessages.stream()
                        .filter(item -> item != null && item.content() != null)
                        .map(ExecutionContext.ConversationMessageContext::content)
                        .toList(), null),
                summary
        ));
        return summary;
    }

    private String compactTextLayer(String key,
                                    String text,
                                    int maxChars,
                                    boolean allowSummary,
                                    ContextCompactionPolicy policy,
                                    PromptContextBudgetService.StageBudget budget,
                                    Map<String, LayerCompaction> layers) {
        int originalChars = safeLength(text);
        String compacted = truncate(text, maxChars);
        boolean summarized = false;
        String strategy = compacted == null || compacted.isBlank() ? "empty" : "truncate";
        if (allowSummary
                && policy.enabled()
                && policy.preferSummaryOverRawHistory()
                && originalChars > maxChars
                && originalChars > maxChars + 80) {
            compacted = summarizeText(text, Math.max(MIN_SUMMARY_LENGTH, maxChars));
            summarized = true;
            strategy = "summary";
        }
        int compactedChars = safeLength(compacted);
        layers.put(key, new LayerCompaction(
                key,
                originalChars,
                compactedChars,
                maxChars,
                estimateTokens(text, budget),
                estimateTokens(compacted, budget),
                promptContextBudgetService.estimateBudgetTokensForChars(maxChars, budget.tokenizerFamily()),
                strategy,
                null,
                summarized,
                compactedChars < originalChars,
                text,
                compacted
        ));
        return compacted;
    }

    private String buildConversationAwareUserMessage(String protectedFactsText,
                                                     String currentUserMessage,
                                                     String conversationStateText,
                                                     String recentConversationSummary,
                                                     List<String> recentUserMessages,
                                                     int maxChars,
                                                     ContextCompactionPolicy policy) {
        StringBuilder builder = new StringBuilder();
        if (policy.preferStateFirst()) {
            appendConversationSections(builder, protectedFactsText, conversationStateText, recentConversationSummary, recentUserMessages, currentUserMessage);
        } else {
            appendCurrentUserFirst(builder, protectedFactsText, currentUserMessage, conversationStateText, recentConversationSummary, recentUserMessages);
        }
        return truncate(builder.toString(), maxChars);
    }

    private String summarizeConversationHistory(ExecutionContext executionContext,
                                                List<ExecutionContext.ConversationMessageContext> recentMessages,
                                                int maxChars) {
        ArrayList<String> fragments = new ArrayList<>();
        if (executionContext != null && executionContext.conversationStateContext() != null
                && executionContext.conversationStateContext().summary() != null
                && !executionContext.conversationStateContext().summary().isBlank()) {
            fragments.add("已确认状态：" + executionContext.conversationStateContext().summary().trim());
        }
        String topics = summarizeTopics(executionContext, null);
        if (!topics.isBlank()) {
            fragments.add(topics);
        }
        if (recentMessages != null && !recentMessages.isEmpty()) {
            ExecutionContext.ConversationMessageContext lastMessage = recentMessages.get(recentMessages.size() - 1);
            if (lastMessage != null && lastMessage.content() != null && !lastMessage.content().isBlank()) {
                String role = "user".equalsIgnoreCase(lastMessage.messageType()) ? "最近用户补充" : "最近助手结论";
                fragments.add(role + "：" + truncate(lastMessage.content().trim(), 100));
            }
        }
        if (fragments.isEmpty()) {
            return null;
        }
        return summarizeTextBlock(fragments, maxChars);
    }

    private ValidationResult validateAndRepairCompaction(String compactedText,
                                                         Map<String, Object> protectedFactsPayload,
                                                         String protectedFactsText,
                                                         String currentUserMessage,
                                                         PromptContextBudgetService.StageBudget budget,
                                                         ContextCompactionPolicy policy) {
        ArrayList<String> missingFacts = new ArrayList<>();
        ArrayList<String> missingProtectedFactKeys = new ArrayList<>();
        List<ProtectedFactCandidate> candidates = flattenProtectedFactCandidates(protectedFactsPayload);
        for (ProtectedFactCandidate candidate : candidates) {
            if (compactedText == null || !containsProtectedFact(compactedText, candidate)) {
                missingProtectedFactKeys.add(candidate.key());
                String displayText = candidate.displayText();
                if (!displayText.isBlank() && !missingFacts.contains(displayText)) {
                    missingFacts.add(displayText);
                }
            }
        }
        if (protectedFactsText != null && !protectedFactsText.isBlank()) {
            for (String line : protectedFactsText.split("\n")) {
                String normalized = line == null ? "" : line.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                if (compactedText == null || !compactedText.contains(normalized)) {
                    missingFacts.add(normalized);
                }
            }
        }
        boolean userMessageLost = currentUserMessage != null
                && !currentUserMessage.isBlank()
                && (compactedText == null || !compactedText.contains(currentUserMessage.trim()));
        String repairedText = compactedText;
        boolean repaired = false;
        if (!missingFacts.isEmpty() || userMessageLost) {
            StringBuilder builder = new StringBuilder();
            if (!missingFacts.isEmpty()) {
                builder.append("保护事实：");
                for (String fact : missingFacts) {
                    builder.append("\n- ").append(fact);
                }
            }
            if (userMessageLost) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append("当前用户问题：").append(currentUserMessage.trim());
            }
            if (compactedText != null && !compactedText.isBlank()) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(compactedText);
            }
            repairedText = truncate(builder.toString(), budget.promptChars());
            repaired = true;
        }
        int scorePenalty = missingFacts.isEmpty() && !userMessageLost
                ? 0
                : Math.min(35, missingFacts.size() * 8 + missingProtectedFactKeys.size() * 4 + (userMessageLost ? 15 : 0));
        return new ValidationResult(
                repairedText,
                repaired,
                userMessageLost,
                candidates.stream().map(ProtectedFactCandidate::key).toList(),
                missingProtectedFactKeys,
                missingFacts,
                Math.max(0, 100 - scorePenalty),
                repaired
                        ? "已自动回填关键事实并重新截断低优先级上下文。"
                        : "关键事实与当前用户问题校验通过。",
                candidates.isEmpty() ? "text" : "structured"
        );
    }

    private List<ProtectedFactCandidate> flattenProtectedFactCandidates(Map<String, Object> protectedFactsPayload) {
        if (protectedFactsPayload == null || protectedFactsPayload.isEmpty()) {
            return List.of();
        }
        ArrayList<ProtectedFactCandidate> candidates = new ArrayList<>();
        appendProtectedFactCandidates(candidates, "confirmedSlots", protectedFactsPayload.get("confirmedSlots"));
        appendProtectedFactCandidates(candidates, "confirmedFacts", protectedFactsPayload.get("confirmedFacts"));
        appendProtectedFactCandidates(candidates, "preferences", protectedFactsPayload.get("preferences"));
        appendProtectedFactCandidates(candidates, "constraints", protectedFactsPayload.get("constraints"));
        appendProtectedFactCandidates(candidates, "goals", protectedFactsPayload.get("goals"));
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private void appendProtectedFactCandidates(List<ProtectedFactCandidate> candidates,
                                               String sectionKey,
                                               Object rawValue) {
        if (!(rawValue instanceof List<?> values) || values.isEmpty()) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            Object item = values.get(index);
            if (item instanceof Map<?, ?> map) {
                String key = safeObjectText(map.get("key"));
                String label = safeObjectText(map.get("label"));
                String value = safeObjectText(map.get("value"));
                String displayText = !label.isBlank() && !value.isBlank()
                        ? label + "=" + value
                        : !key.isBlank() && !value.isBlank()
                        ? key + "=" + value
                        : value;
                if (!displayText.isBlank()) {
                    candidates.add(new ProtectedFactCandidate(
                            sectionKey + "." + (key.isBlank() ? index : key),
                            displayText,
                            List.of(displayText, value, label, key)
                    ));
                }
                continue;
            }
            String text = safeObjectText(item);
            if (!text.isBlank()) {
                candidates.add(new ProtectedFactCandidate(
                        sectionKey + "." + index,
                        text,
                        List.of(text)
                ));
            }
        }
    }

    private boolean containsProtectedFact(String compactedText, ProtectedFactCandidate candidate) {
        if (compactedText == null || candidate == null) {
            return false;
        }
        for (String token : candidate.matchTokens()) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isBlank() && compactedText.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String mergeLayeredSummaryWithText(String layeredConversationSummary, String conversationMemory) {
        if ((layeredConversationSummary == null || layeredConversationSummary.isBlank())
                && (conversationMemory == null || conversationMemory.isBlank())) {
            return conversationMemory;
        }
        if (layeredConversationSummary == null || layeredConversationSummary.isBlank()) {
            return conversationMemory;
        }
        if (conversationMemory == null || conversationMemory.isBlank()) {
            return layeredConversationSummary;
        }
        return layeredConversationSummary + "\n" + conversationMemory;
    }

    private String buildShortTermSummary(ExecutionContext executionContext,
                                         String userMessage,
                                         String assistantContent) {
        ArrayList<String> fragments = new ArrayList<>();
        if (userMessage != null && !userMessage.isBlank()) {
            fragments.add("本轮用户输入：" + truncate(userMessage.trim(), 120));
        }
        if (assistantContent != null && !assistantContent.isBlank()) {
            fragments.add("本轮助手结论：" + truncate(assistantContent.replace("\n", " ").trim(), 140));
        }
        return summarizeTextBlock(fragments, 240);
    }

    private String buildMediumTermSummary(ExecutionContext executionContext,
                                          String rollingSummary) {
        ArrayList<String> fragments = new ArrayList<>();
        if (rollingSummary != null && !rollingSummary.isBlank()) {
            fragments.add("滚动摘要：" + truncate(rollingSummary.trim(), 180));
        }
        String topics = summarizeTopics(executionContext, null);
        if (!topics.isBlank()) {
            fragments.add(topics);
        }
        return summarizeTextBlock(fragments, 260);
    }

    private String buildLongTermSummary(ExecutionContext executionContext,
                                        String rollingSummary) {
        ArrayList<String> fragments = new ArrayList<>();
        if (executionContext != null && executionContext.protectedStateFactsText() != null
                && !executionContext.protectedStateFactsText().isBlank()) {
            fragments.add("关键事实沉淀：" + truncate(executionContext.protectedStateFactsText().replace("\n", " | "), 220));
        }
        if (rollingSummary != null && !rollingSummary.isBlank()) {
            fragments.add("长期目标线索：" + truncate(rollingSummary.trim(), 120));
        }
        return summarizeTextBlock(fragments, 280);
    }

    private String summarizeTopics(ExecutionContext executionContext, String latestUserMessage) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        if (executionContext != null && executionContext.recentConversationMessages() != null) {
            for (ExecutionContext.ConversationMessageContext message : executionContext.recentConversationMessages()) {
                if (message == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                if ("user".equalsIgnoreCase(message.messageType())) {
                    topics.addAll(extractTopics(message.content()));
                }
            }
        }
        if (latestUserMessage != null && !latestUserMessage.isBlank()) {
            topics.addAll(extractTopics(latestUserMessage));
        }
        if (topics.isEmpty()) {
            return "";
        }
        return "持续主题：" + String.join("、", topics.stream().limit(5).toList());
    }

    private void fillSummaryLayerMetadata(ObjectNode node,
                                          String layer,
                                          String summary,
                                          String promotionRule,
                                          int sourceCount) {
        node.put("layer", layer);
        node.put("updatedAt", LocalDateTime.now().toString());
        node.put("promotionRule", promotionRule);
        node.put("sourceCount", sourceCount);
        node.put("summaryChars", safeLength(summary));
        node.put("empty", summary == null || summary.isBlank());
    }

    private List<String> extractTopics(String text) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        Matcher matcher = TOPIC_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String item = matcher.group().trim();
            if (item.length() < 2 || STOP_WORDS.contains(item)) {
                continue;
            }
            topics.add(item);
            if (topics.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(topics);
    }

    private Map<String, Object> toMetadata(PromptContextBudgetService.StageBudget budget,
                                           Map<String, LayerCompaction> layers,
                                           ContextCompactionPolicy policy,
                                           ValidationResult validationResult) {
        List<Map<String, Object>> items = new ArrayList<>();
        int originalChars = 0;
        int compactedChars = 0;
        int originalTokens = 0;
        int compactedTokens = 0;
        boolean summarized = false;
        boolean truncated = false;
        for (LayerCompaction layer : layers.values()) {
            originalChars += layer.originalChars();
            compactedChars += layer.compactedChars();
            originalTokens += layer.originalTokens();
            compactedTokens += layer.compactedTokens();
            summarized = summarized || layer.summarized();
            truncated = truncated || layer.truncated();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("key", layer.key());
            item.put("originalChars", layer.originalChars());
            item.put("compactedChars", layer.compactedChars());
            item.put("budgetChars", layer.budgetChars());
            item.put("originalTokens", layer.originalTokens());
            item.put("compactedTokens", layer.compactedTokens());
            item.put("budgetTokens", layer.budgetTokens());
            item.put("strategy", layer.strategy());
            item.put("itemCount", layer.itemCount());
            item.put("summarized", layer.summarized());
            item.put("truncated", layer.truncated());
            item.put("sampleBefore", truncate(layer.sampleBefore(), 120));
            item.put("sampleAfter", truncate(layer.sampleAfter(), 120));
            items.add(item);
        }
        CompactionQuality quality = evaluateQuality(layers, originalChars, compactedChars, policy);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", budget.stage().name().toLowerCase());
        metadata.put("contextWindow", budget.contextWindow());
        metadata.put("tokenizerFamily", budget.tokenizerFamily());
        metadata.put("tokenEstimateMode", promptContextBudgetService.estimateMode(budget.tokenizerFamily()));
        metadata.put("usablePromptTokens", budget.usablePromptTokens());
        metadata.put("promptChars", budget.promptChars());
        metadata.put("promptEstimatedTokens", promptContextBudgetService.estimateBudgetTokensForChars(budget.promptChars(), budget.tokenizerFamily()));
        metadata.put("originalChars", originalChars);
        metadata.put("compactedChars", compactedChars);
        metadata.put("savedChars", Math.max(0, originalChars - compactedChars));
        metadata.put("estimatedOriginalTokens", originalTokens);
        metadata.put("estimatedCompactedTokens", compactedTokens);
        metadata.put("savedTokens", Math.max(0, originalTokens - compactedTokens));
        metadata.put("summarized", summarized);
        metadata.put("truncated", truncated);
        metadata.put("qualityScore", quality.score());
        metadata.put("qualityStatus", quality.status());
        metadata.put("qualityWarnings", quality.warnings());
        metadata.put("policy", policy.toMetadata());
        metadata.put("validation", validationResult.toMetadata());
        metadata.put("protectedFactKeys", validationResult.protectedFactKeys());
        metadata.put("missingProtectedFactKeys", validationResult.missingProtectedFactKeys());
        metadata.put("promptAssembly", buildPromptAssemblyMetadata(budget.stage(), layers));
        metadata.put("explain", explainCompaction(metadata));
        metadata.put("layers", items);
        return metadata;
    }

    private Map<String, Object> buildPromptAssemblyMetadata(PromptContextBudgetService.PromptStage stage,
                                                            Map<String, LayerCompaction> layers) {
        ArrayList<PromptAssemblyBlock> blocks = new ArrayList<>();
        for (PromptAssemblyConfig config : resolvePromptAssemblyConfigs(stage)) {
            LayerCompaction layer = layers.get(config.layerKey());
            if (layer == null) {
                blocks.add(new PromptAssemblyBlock(
                        config.blockKey(),
                        config.label(),
                        config.layerKey(),
                        config.source(),
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        false,
                        ""
                ));
                continue;
            }
            blocks.add(new PromptAssemblyBlock(
                    config.blockKey(),
                    config.label(),
                    config.layerKey(),
                    config.source(),
                    layer.originalChars(),
                    layer.compactedChars(),
                    layer.originalTokens(),
                    layer.compactedTokens(),
                    layer.compactedChars() > 0,
                    layer.truncated(),
                    layer.summarized(),
                    truncate(layer.sampleAfter(), 160)
            ));
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", stage == null ? "" : stage.name().toLowerCase());
        metadata.put("blockOrder", blocks.stream().map(PromptAssemblyBlock::blockKey).toList());
        metadata.put("blocks", blocks.stream().map(PromptAssemblyBlock::toMetadata).toList());
        metadata.put("preview", blocks.stream()
                .filter(PromptAssemblyBlock::present)
                .map(item -> item.label() + "：" + safeText(item.sample()))
                .filter(item -> !item.endsWith("："))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
        return metadata;
    }

    private List<PromptAssemblyConfig> resolvePromptAssemblyConfigs(PromptContextBudgetService.PromptStage stage) {
        ArrayList<PromptAssemblyConfig> configs = new ArrayList<>();
        configs.add(new PromptAssemblyConfig("conversationAwareUserMessage", "用户消息聚合", "conversationAwareUserMessage", "runtime"));
        configs.add(new PromptAssemblyConfig("protectedFacts", "保护事实", "protectedFactsText", "state"));
        configs.add(new PromptAssemblyConfig("conversationState", "会话状态", "conversationStateText", "state"));
        configs.add(new PromptAssemblyConfig("recentConversationSummary", "历史对话摘要", "recentConversationSummary", "history"));
        configs.add(new PromptAssemblyConfig("recentUserMessages", "最近用户消息", "recentUserMessages", "history"));
        configs.add(new PromptAssemblyConfig("conversationMemory", "会话记忆", "conversationMemory", "memory"));
        configs.add(new PromptAssemblyConfig("userMemory", "用户记忆", "userMemory", "memory"));
        configs.add(new PromptAssemblyConfig("recentTaskMemory", "最近任务记忆", "recentTaskMemory", "memory"));
        if (stage == PromptContextBudgetService.PromptStage.EXECUTION_INPUT) {
            configs.add(0, new PromptAssemblyConfig("currentUserMessage", "当前用户问题", "currentUserMessage", "runtime"));
        }
        return configs;
    }

    private CompactionQuality evaluateQuality(Map<String, LayerCompaction> layers,
                                              int originalChars,
                                              int compactedChars,
                                              ContextCompactionPolicy policy) {
        int score = 100;
        ArrayList<String> warnings = new ArrayList<>();
        LayerCompaction userMessage = layers.get("currentUserMessage");
        LayerCompaction conversationState = layers.get("conversationStateText");
        LayerCompaction conversationMemory = layers.get("conversationMemory");
        LayerCompaction userMemory = layers.get("userMemory");
        LayerCompaction recentConversationSummary = layers.get("recentConversationSummary");
        if (userMessage != null && userMessage.originalChars() > 0 && userMessage.compactedChars() == 0) {
            score -= 60;
            warnings.add("当前用户问题在压缩后为空，存在严重语义丢失风险。");
        }
        if (conversationState != null && conversationState.originalChars() > 0
                && conversationState.compactedChars() < Math.min(24, conversationState.originalChars())) {
            score -= 20;
            warnings.add("会话状态被压得过短，可能影响槽位连续性。");
        }
        if (conversationMemory != null && conversationMemory.originalChars() > 0
                && conversationMemory.summarized() && conversationMemory.compactedChars() < 60) {
            score -= 10;
            warnings.add("会话记忆被强压缩到较短摘要，建议关注关键事实是否保留。");
        }
        if (userMemory != null && userMemory.originalChars() > 0
                && userMemory.summarized() && userMemory.compactedChars() < 60) {
            score -= 10;
            warnings.add("用户长期记忆被强压缩，可能影响跨轮偏好和长期事实。");
        }
        if (recentConversationSummary != null && recentConversationSummary.originalChars() > 0
                && !recentConversationSummary.summarized()) {
            score -= 5;
            warnings.add("历史窗口未触发摘要压缩，长会话下可能仍有上下文膨胀风险。");
        }
        if (originalChars > 0) {
            double retainedRatio = compactedChars / (double) originalChars;
            if (retainedRatio < policy.minRetentionRatio()) {
                score -= 20;
                warnings.add("整体压缩比例过高，保留内容偏少。");
            } else if (retainedRatio < Math.max(policy.minRetentionRatio() + 0.1D, 0.28D)) {
                score -= 10;
                warnings.add("整体压缩比例较高，建议关注细节损失。");
            }
        }
        if (score < policy.qualityWarningThreshold()) {
            warnings.add("当前压缩质量已低于 Agent 配置阈值，建议缩短历史窗口或切换大上下文模型。");
        }
        score = Math.max(0, Math.min(100, score));
        String status = score >= 80 ? "good" : score >= 55 ? "warning" : "critical";
        return new CompactionQuality(score, status, warnings);
    }

    private void appendConversationSections(StringBuilder builder,
                                            String protectedFactsText,
                                            String conversationStateText,
                                            String recentConversationSummary,
                                            List<String> recentUserMessages,
                                            String currentUserMessage) {
        if (protectedFactsText != null && !protectedFactsText.isBlank()) {
            builder.append("保护事实：").append(protectedFactsText.trim());
        }
        if (conversationStateText != null && !conversationStateText.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("会话状态：").append(conversationStateText.trim());
        }
        if (recentConversationSummary != null && !recentConversationSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("历史对话摘要：").append(recentConversationSummary.trim());
        }
        if (recentUserMessages != null && !recentUserMessages.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("最近用户输入：");
            for (int index = 0; index < recentUserMessages.size(); index++) {
                builder.append("\n").append(index + 1).append(". ").append(recentUserMessages.get(index));
            }
        }
        if (currentUserMessage != null && !currentUserMessage.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("当前用户问题：").append(currentUserMessage.trim());
        }
    }

    private void appendCurrentUserFirst(StringBuilder builder,
                                        String protectedFactsText,
                                        String currentUserMessage,
                                        String conversationStateText,
                                        String recentConversationSummary,
                                        List<String> recentUserMessages) {
        if (currentUserMessage != null && !currentUserMessage.isBlank()) {
            builder.append("当前用户问题：").append(currentUserMessage.trim());
        }
        if (protectedFactsText != null && !protectedFactsText.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("保护事实：").append(protectedFactsText.trim());
        }
        if (conversationStateText != null && !conversationStateText.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("会话状态：").append(conversationStateText.trim());
        }
        if (recentConversationSummary != null && !recentConversationSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("历史对话摘要：").append(recentConversationSummary.trim());
        }
        if (recentUserMessages != null && !recentUserMessages.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("最近用户输入：");
            for (int index = 0; index < recentUserMessages.size(); index++) {
                builder.append("\n").append(index + 1).append(". ").append(recentUserMessages.get(index));
            }
        }
    }

    private int estimateTokens(String text, PromptContextBudgetService.StageBudget budget) {
        return promptContextBudgetService.estimateTokens(text, budget == null ? null : budget.tokenizerFamily());
    }

    private int estimateTokens(String first, String second, String third, PromptContextBudgetService.StageBudget budget) {
        return estimateTokens(joinLines(first, second, third), budget);
    }

    private int estimateTokensByMessages(List<ExecutionContext.ConversationMessageContext> messages,
                                         String role,
                                         PromptContextBudgetService.StageBudget budget) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        ArrayList<String> values = new ArrayList<>();
        for (ExecutionContext.ConversationMessageContext message : messages) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            if (role != null && !role.equalsIgnoreCase(message.messageType())) {
                continue;
            }
            values.add(message.content());
        }
        return estimateTokens(joinLines(values, null), budget);
    }

    private String joinLines(List<String> values, String append) {
        ArrayList<String> lines = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    lines.add(value);
                }
            }
        }
        if (append != null && !append.isBlank()) {
            lines.add(append);
        }
        return String.join("\n", lines);
    }

    private String joinLines(String... values) {
        ArrayList<String> lines = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    lines.add(value);
                }
            }
        }
        return String.join("\n", lines);
    }

    private String summarizeText(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return text;
        }
        List<String> lines = List.of(text.replace("\r", "").split("\n"));
        return summarizeTextBlock(lines, maxChars);
    }

    private String summarizeTextBlock(List<String> lines, int maxChars) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        ArrayList<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String value = line.trim();
            if (!value.isEmpty() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int takeHead = Math.min(2, normalized.size());
        for (int index = 0; index < takeHead; index++) {
            appendSummaryLine(builder, normalized.get(index));
        }
        if (normalized.size() > 3) {
            appendSummaryLine(builder, "中间细节已压缩，共 " + normalized.size() + " 条上下文片段。");
        }
        int tailStart = Math.max(takeHead, normalized.size() - 1);
        for (int index = tailStart; index < normalized.size(); index++) {
            appendSummaryLine(builder, normalized.get(index));
        }
        return truncate(builder.toString(), maxChars);
    }

    private void appendSummaryLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(line);
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    private int countUserMessages(List<ExecutionContext.ConversationMessageContext> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ExecutionContext.ConversationMessageContext message : recentMessages) {
            if (message != null && "user".equalsIgnoreCase(message.messageType())) {
                count++;
            }
        }
        return count;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String safeObjectText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private ObjectNode parseObject(String text) {
        try {
            if (text == null || text.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return (ObjectNode) objectMapper.readTree(text);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private record LayerCompaction(
            String key,
            int originalChars,
            int compactedChars,
            int budgetChars,
            int originalTokens,
            int compactedTokens,
            int budgetTokens,
            String strategy,
            Integer itemCount,
            boolean summarized,
            boolean truncated,
            String sampleBefore,
            String sampleAfter
    ) {
    }

    private record CompactionQuality(
            int score,
            String status,
            List<String> warnings
    ) {
    }

    private record ValidationResult(
            String compactedText,
            boolean repaired,
            boolean userMessageLost,
            List<String> protectedFactKeys,
            List<String> missingProtectedFactKeys,
            List<String> missingProtectedFacts,
            int confidenceScore,
            String message,
            String validationMode
    ) {

        static ValidationResult empty() {
            return new ValidationResult(null, false, false, List.of(), List.of(), List.of(), 100, "未执行回填校验。", "text");
        }

        boolean requiresRetry() {
            return userMessageLost || !missingProtectedFacts.isEmpty();
        }

        Map<String, Object> toMetadata() {
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("repaired", repaired);
            metadata.put("userMessageLost", userMessageLost);
            metadata.put("protectedFactKeys", protectedFactKeys);
            metadata.put("missingProtectedFactKeys", missingProtectedFactKeys);
            metadata.put("missingProtectedFacts", missingProtectedFacts);
            metadata.put("confidenceScore", confidenceScore);
            metadata.put("message", message);
            metadata.put("validationMode", validationMode);
            return metadata;
        }
    }

    private record ProtectedFactCandidate(
            String key,
            String displayText,
            List<String> matchTokens
    ) {
    }

    private record PromptAssemblyConfig(
            String blockKey,
            String label,
            String layerKey,
            String source
    ) {
    }

    private record PromptAssemblyBlock(
            String blockKey,
            String label,
            String layerKey,
            String source,
            int originalChars,
            int compactedChars,
            int originalTokens,
            int compactedTokens,
            boolean present,
            boolean truncated,
            boolean summarized,
            String sample
    ) {

        Map<String, Object> toMetadata() {
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("blockKey", blockKey);
            metadata.put("label", label);
            metadata.put("layerKey", layerKey);
            metadata.put("source", source);
            metadata.put("originalChars", originalChars);
            metadata.put("compactedChars", compactedChars);
            metadata.put("originalTokens", originalTokens);
            metadata.put("compactedTokens", compactedTokens);
            metadata.put("present", present);
            metadata.put("truncated", truncated);
            metadata.put("summarized", summarized);
            metadata.put("sample", sample);
            return metadata;
        }
    }

    public record StageCompactionBundle(
            String stage,
            String currentUserMessage,
            Map<String, Object> protectedFactsPayload,
            String conversationStateText,
            String conversationAwareUserMessage,
            String recentConversationSummary,
            List<String> recentUserMessages,
            String conversationMemory,
            String userMemory,
            String recentTaskMemory,
            Map<String, Object> metadata
    ) {
    }

    public record CompactedList(
            List<String> items,
            String summary,
            Map<String, Object> metadata
    ) {
    }
}
