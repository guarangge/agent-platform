package com.zc.agent.core.modules.memory.service;

import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.memory.model.MemoryUpdateDraft;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.ContextCompactionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Memory 更新服务。
 */
@Service
public class MemoryUpdaterService {

    private static final int MEMORY_SHORT_TEXT_MAX_LENGTH = 500;
    private static final Pattern TOPIC_PATTERN = Pattern.compile("[\\p{IsHan}]{2,8}|[A-Za-z][A-Za-z0-9_-]{2,20}");
    private static final Set<String> TOPIC_STOP_WORDS = Set.of(
            "请帮我", "帮我", "请问", "一下", "这个", "那个", "以及", "我们", "你们", "现在", "需要",
            "如何", "怎么", "什么", "哪些", "可以", "继续", "处理", "分析", "建议", "问题", "当前"
    );

    private final ConversationRepository conversationRepository;
    private final ConversationStateService conversationStateService;
    private final MemoryLlmNormalizationService memoryLlmNormalizationService;
    private final ContextCompactionService contextCompactionService;
    private final MemoryGovernanceService memoryGovernanceService;

    public MemoryUpdaterService(ConversationRepository conversationRepository,
                                ConversationStateService conversationStateService,
                                MemoryLlmNormalizationService memoryLlmNormalizationService,
                                ContextCompactionService contextCompactionService,
                                MemoryGovernanceService memoryGovernanceService) {
        this.conversationRepository = conversationRepository;
        this.conversationStateService = conversationStateService;
        this.memoryLlmNormalizationService = memoryLlmNormalizationService;
        this.contextCompactionService = contextCompactionService;
        this.memoryGovernanceService = memoryGovernanceService;
    }

    /**
     * 更新一次消息执行后的全部记忆。
     *
     * @param conversationId    会话 ID
     * @param taskId            任务 ID
     * @param agentName         Agent 名称
     * @param userMessage       用户消息
     * @param assistantContent  助手响应
     * @param executionContext  执行上下文
     * @param executionPlan     执行计划
     * @param operatorId        操作人
     */
    public void updateAfterMessage(Long conversationId,
                                   Long taskId,
                                   String agentName,
                                   String userMessage,
                                   String assistantContent,
                                   ExecutionContext executionContext,
                                   ExecutionPlan executionPlan,
                                   Long operatorId) {
        MemoryUpdateDraft memoryUpdateDraft = buildRuleDraft(
                agentName,
                userMessage,
                assistantContent,
                executionContext,
                executionPlan
        );
        MemoryUpdateDraft normalizedDraft = memoryLlmNormalizationService.normalize(memoryUpdateDraft);
        conversationRepository.upsertConversationMemory(
                conversationId,
                normalizeSummaryText(normalizedDraft.conversationMemorySummary(), memoryUpdateDraft.conversationMemorySummary()),
                normalizeDistinctMemoryText(normalizedDraft.conversationPreferenceSummary(), memoryUpdateDraft.conversationPreferenceSummary()),
                truncateText(normalizedDraft.lastUserMessage(), MEMORY_SHORT_TEXT_MAX_LENGTH),
                truncateText(normalizedDraft.lastAssistantSummary(), MEMORY_SHORT_TEXT_MAX_LENGTH),
                operatorId
        );
        conversationRepository.findConversationMemoryByConversationId(conversationId).ifPresent(conversationMemoryRecord -> {
            String updatedMetadataJson = conversationStateService.updateConversationMetadata(
                    executionContext.agentContext().metadata(),
                    conversationMemoryRecord.metadata() == null ? "{}" : conversationMemoryRecord.metadata().toString(),
                    executionContext,
                    assistantContent
            );
            updatedMetadataJson = contextCompactionService.mergeConversationCompactionMetadata(
                    updatedMetadataJson,
                    executionContext,
                    normalizeSummaryText(normalizedDraft.conversationMemorySummary(), memoryUpdateDraft.conversationMemorySummary()),
                    userMessage,
                    assistantContent
            );
            conversationRepository.updateConversationMemoryMetadata(conversationId, updatedMetadataJson, operatorId);
        });
        if (executionContext.userMemoryPolicyContext() == null || executionContext.userMemoryPolicyContext().userMemoryWritebackEnabled()) {
            String preference = normalizeDistinctMemoryText(normalizedDraft.userPreferenceSummary(), memoryUpdateDraft.userPreferenceSummary());
            String attention = normalizeDistinctMemoryText(normalizedDraft.userAttentionSummary(), memoryUpdateDraft.userAttentionSummary());
            String fact = normalizeDistinctMemoryText(normalizedDraft.userFactSummary(), memoryUpdateDraft.userFactSummary());
            String goal = normalizeDistinctMemoryText(normalizedDraft.userGoalSummary(), memoryUpdateDraft.userGoalSummary());
            conversationRepository.upsertUserMemory(
                    executionContext.currentUser().userId(),
                    executionContext.agentContext().agentId(),
                    preference,
                    attention,
                    fact,
                    goal,
                    operatorId
            );
            memoryGovernanceService.persist(executionContext, taskId, Map.of(
                    "preference", preference,
                    "attention", attention,
                    "fact", fact,
                    "goal", goal
            ), operatorId);
        }
        conversationRepository.upsertTaskMemorySnapshot(
                taskId,
                conversationId,
                normalizeSummaryText(normalizedDraft.taskMemorySummary(), memoryUpdateDraft.taskMemorySummary()),
                normalizeSummaryText(normalizedDraft.taskKeyFindings(), memoryUpdateDraft.taskKeyFindings()),
                normalizeSummaryText(normalizedDraft.taskFinalResponseSummary(), memoryUpdateDraft.taskFinalResponseSummary()),
                operatorId
        );
    }

    private MemoryUpdateDraft buildRuleDraft(String agentName,
                                             String userMessage,
                                             String assistantContent,
                                             ExecutionContext executionContext,
                                             ExecutionPlan executionPlan) {
        ExecutionContext.UserMemoryContext userMemoryContext = executionContext.userMemoryContext();
        String existingPreferenceSummary = userMemoryContext == null ? null : userMemoryContext.preferenceSummary();
        String existingAttentionSummary = userMemoryContext == null ? null : userMemoryContext.attentionSummary();
        String existingFactSummary = userMemoryContext == null ? null : userMemoryContext.factSummary();
        String existingGoalSummary = userMemoryContext == null ? null : userMemoryContext.goalSummary();
        return new MemoryUpdateDraft(
                userMessage,
                assistantContent,
                buildConversationMemorySummary(agentName, userMessage, assistantContent, executionContext),
                buildConversationPreferenceSummary(userMessage),
                truncateText(userMessage, 500),
                truncateText(assistantContent, 500),
                buildUserPreferenceSummary(existingPreferenceSummary, userMessage),
                buildUserAttentionSummary(existingAttentionSummary, userMessage),
                buildUserFactSummary(existingFactSummary, userMessage),
                buildUserGoalSummary(existingGoalSummary, userMessage),
                buildTaskMemorySummary(executionPlan, normalizeAssistantSummary(assistantContent)),
                buildTaskKeyFindings(assistantContent),
                truncateText(assistantContent, 500)
        );
    }

    private String buildConversationMemorySummary(String agentName,
                                                  String userMessage,
                                                  String assistantContent,
                                                  ExecutionContext executionContext) {
        String existingSummary = executionContext.conversationMemoryContext() == null
                ? null
                : executionContext.conversationMemoryContext().memorySummary();
        String rollingSummary = contextCompactionService.buildRollingConversationSummary(
                existingSummary,
                executionContext,
                userMessage,
                assistantContent
        );
        return "当前会话围绕「" + agentName + "」进行。\n" + rollingSummary;
    }

    private String buildConversationPreferenceSummary(String userMessage) {
        ArrayList<String> preferences = new ArrayList<>();
        preferences.addAll(extractStylePreferences(userMessage));
        preferences.addAll(extractTopicPhrases(userMessage, "关注", 3));
        if (preferences.isEmpty()) {
            return "当前偏好待继续观察";
        }
        return mergeDistinctMemoryItems(preferences, "当前偏好待继续观察");
    }

    private String buildUserPreferenceSummary(String existingPreferenceSummary, String userMessage) {
        ArrayList<String> preferences = new ArrayList<>();
        if (existingPreferenceSummary != null && !existingPreferenceSummary.isBlank()) {
            preferences.add(existingPreferenceSummary);
        }
        preferences.addAll(extractStylePreferences(userMessage));
        return mergeDistinctMemoryItems(preferences, "当前偏好待继续观察");
    }

    private String buildUserAttentionSummary(String existingAttentionSummary, String userMessage) {
        ArrayList<String> attentionItems = new ArrayList<>();
        if (existingAttentionSummary != null && !existingAttentionSummary.isBlank()) {
            attentionItems.add(existingAttentionSummary);
        }
        attentionItems.addAll(extractTopicPhrases(userMessage, "持续关注", 3));
        return mergeDistinctMemoryItems(attentionItems, "当前关注点待继续观察");
    }

    private String buildUserFactSummary(String existingFactSummary, String userMessage) {
        ArrayList<String> factItems = new ArrayList<>();
        if (existingFactSummary != null && !existingFactSummary.isBlank()) {
            factItems.add(existingFactSummary);
        }
        if (userMessage.contains("我是")) {
            factItems.add("用户在对话中主动补充了身份信息");
        }
        if (userMessage.contains("当前使用") || userMessage.contains("目前使用")) {
            factItems.add("用户提供了当前使用环境信息");
        }
        return mergeDistinctMemoryItems(factItems, "长期事实待继续沉淀");
    }

    private String buildUserGoalSummary(String existingGoalSummary, String userMessage) {
        ArrayList<String> goalItems = new ArrayList<>();
        if (existingGoalSummary != null && !existingGoalSummary.isBlank()) {
            goalItems.add(existingGoalSummary);
        }
        if (containsRequestIntent(userMessage)) {
            goalItems.add("希望获得当前问题的可执行结果");
        }
        goalItems.addAll(extractTopicPhrases(userMessage, "希望推进", 2));
        return mergeDistinctMemoryItems(goalItems, "长期目标待继续沉淀");
    }

    private String buildTaskMemorySummary(ExecutionPlan executionPlan, String stepSummaryText) {
        return "本次任务按「" + executionPlan.planType() + "」执行，步骤摘要为「"
                + truncateText(stepSummaryText, 200)
                + "」。";
    }

    private String buildTaskKeyFindings(String assistantContent) {
        return truncateText(assistantContent, 500);
    }

    private String normalizeSummaryText(String preferredValue, String fallbackValue) {
        if (preferredValue == null || preferredValue.isBlank()) {
            return fallbackValue;
        }
        return preferredValue.trim();
    }

    private String normalizeDistinctMemoryText(String preferredValue, String fallbackValue) {
        if (preferredValue == null || preferredValue.isBlank()) {
            return truncateText(fallbackValue, MEMORY_SHORT_TEXT_MAX_LENGTH);
        }
        return truncateText(
                mergeDistinctMemoryItems(List.of(preferredValue), fallbackValue),
                MEMORY_SHORT_TEXT_MAX_LENGTH
        );
    }

    private String normalizeAssistantSummary(String assistantContent) {
        return truncateText(assistantContent.replace("\n", " ").trim(), 220);
    }

    private String mergeDistinctMemoryItems(List<String> sourceItems, String fallbackText) {
        ArrayList<String> mergedItems = new ArrayList<>();
        for (String sourceItem : sourceItems) {
            if (sourceItem == null || sourceItem.isBlank()) {
                continue;
            }
            String[] splitItems = sourceItem.split("；");
            for (String splitItem : splitItems) {
                String normalizedItem = splitItem.trim();
                if (!normalizedItem.isEmpty() && !mergedItems.contains(normalizedItem)) {
                    mergedItems.add(normalizedItem);
                }
            }
        }
        if (mergedItems.isEmpty()) {
            return fallbackText;
        }
        return String.join("；", mergedItems);
    }

    private String truncateText(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        if (maxLength <= 3) {
            return content.substring(0, maxLength);
        }
        return content.substring(0, maxLength - 3) + "...";
    }

    private List<String> extractStylePreferences(String userMessage) {
        ArrayList<String> preferences = new ArrayList<>();
        if (userMessage.contains("简洁")) {
            preferences.add("偏好简洁回答");
        }
        if (userMessage.contains("详细")) {
            preferences.add("偏好详细说明");
        }
        if (userMessage.contains("一步一步") || userMessage.contains("分步骤")) {
            preferences.add("偏好分步骤说明");
        }
        if (userMessage.contains("示例")) {
            preferences.add("偏好示例说明");
        }
        if (userMessage.contains("代码")) {
            preferences.add("偏好代码实现");
        }
        return preferences;
    }

    private List<String> extractTopicPhrases(String userMessage, String prefix, int limit) {
        LinkedHashSet<String> topics = extractTopics(userMessage, limit);
        ArrayList<String> phrases = new ArrayList<>();
        for (String topic : topics) {
            phrases.add(prefix + topic);
        }
        return phrases;
    }

    private LinkedHashSet<String> extractTopics(String userMessage, int limit) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        Matcher matcher = TOPIC_PATTERN.matcher(userMessage);
        while (matcher.find() && topics.size() < limit) {
            String topic = matcher.group().trim();
            if (topic.length() < 2 || TOPIC_STOP_WORDS.contains(topic)) {
                continue;
            }
            topics.add(topic);
        }
        return topics;
    }

    private boolean containsRequestIntent(String userMessage) {
        return userMessage.contains("请")
                || userMessage.contains("帮我")
                || userMessage.contains("需要")
                || userMessage.contains("想")
                || userMessage.contains("如何")
                || userMessage.contains("怎么");
    }
}
