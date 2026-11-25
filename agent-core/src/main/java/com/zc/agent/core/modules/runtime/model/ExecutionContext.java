package com.zc.agent.core.modules.runtime.model;

import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 Agent 执行所需的统一运行时上下文。
 *
 * @param currentUser         当前登录用户上下文
 * @param conversationMemoryContext 当前会话记忆上下文
 * @param conversationStateContext  当前会话槽位状态上下文
 * @param userMemoryPolicyContext 当前会话用户记忆策略上下文
 * @param userMemoryContext   当前用户记忆上下文
 * @param recentTaskMemoryContext 最近一次任务记忆上下文
 * @param recentConversationMessages 最近对话窗口消息
 * @param conversationContext 当前会话上下文
 * @param agentContext        当前 Agent 上下文
 * @param promptContext       当前 Prompt 上下文
 * @param decisionPromptContext 决策 Prompt 上下文
 * @param plannerPromptContext  规划 Prompt 上下文
 * @param timeContext         当前时间上下文
 * @param modelContext        当前模型上下文
 * @param stageModelContexts  按执行阶段配置的模型上下文
 * @param messageContext      当前消息上下文
 * @param taskContext         当前任务上下文
 * @param availableTools      当前可用 Tool 列表
 * @param availableWorkflows  当前可用 Workflow 列表
 * @param availableDelegates  当前可委派 Agent 列表
 */
public record ExecutionContext(
        CurrentUserContext currentUser,
        ConversationMemoryContext conversationMemoryContext,
        ConversationStateContext conversationStateContext,
        UserMemoryPolicyContext userMemoryPolicyContext,
        UserMemoryContext userMemoryContext,
        RecentTaskMemoryContext recentTaskMemoryContext,
        List<ConversationMessageContext> recentConversationMessages,
        ConversationContext conversationContext,
        AgentContext agentContext,
        PromptContext promptContext,
        PromptContext decisionPromptContext,
        PromptContext plannerPromptContext,
        TimeContext timeContext,
        ModelContext modelContext,
        Map<String, ModelContext> stageModelContexts,
        MessageContext messageContext,
        TaskContext taskContext,
        List<RegistryAgentTool> availableTools,
        List<RegistryAgentWorkflow> availableWorkflows,
        List<DelegateAgent> availableDelegates
) {

    /**
     * 返回当前会话记忆摘要文本。
     *
     * @return 会话记忆摘要
     */
    public String conversationMemoryText() {
        String memorySummary = conversationMemoryContext == null ? null : conversationMemoryContext.memorySummary();
        String preferenceSummary = conversationMemoryContext == null ? null : conversationMemoryContext.preferenceSummary();
        String lastUserMessage = conversationMemoryContext == null ? null : conversationMemoryContext.lastUserMessage();
        String lastAssistantSummary = conversationMemoryContext == null ? null : conversationMemoryContext.lastAssistantSummary();
        String layeredSummary = layeredConversationSummaryText();
        String recentConversationText = recentConversationText();
        if ((memorySummary == null || memorySummary.isBlank())
                && (preferenceSummary == null || preferenceSummary.isBlank())
                && (lastUserMessage == null || lastUserMessage.isBlank())
                && (lastAssistantSummary == null || lastAssistantSummary.isBlank())
                && (layeredSummary == null || layeredSummary.isBlank())
                && (recentConversationText == null || recentConversationText.isBlank())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (layeredSummary != null && !layeredSummary.isBlank()) {
            builder.append(layeredSummary);
        }
        if (memorySummary != null && !memorySummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("会话摘要：").append(memorySummary);
        }
        if (preferenceSummary != null && !preferenceSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("会话偏好：").append(preferenceSummary);
        }
        if (lastUserMessage != null && !lastUserMessage.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("最近用户消息：").append(lastUserMessage);
        }
        if (lastAssistantSummary != null && !lastAssistantSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("最近助手摘要：").append(lastAssistantSummary);
        }
        if (recentConversationText != null && !recentConversationText.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(recentConversationText);
        }
        String conversationStateText = conversationStateText();
        if (conversationStateText != null && !conversationStateText.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(conversationStateText);
        }
        return builder.toString();
    }

    /**
     * 返回会话级分层摘要文本。
     *
     * @return 分层摘要
     */
    public String layeredConversationSummaryText() {
        if (conversationMemoryContext == null || conversationMemoryContext.metadata() == null) {
            return null;
        }
        JsonNode summaryLayers = conversationMemoryContext.metadata().path("contextCompaction").path("summaryLayers");
        if (summaryLayers.isMissingNode() || !summaryLayers.isObject()) {
            return null;
        }
        String shortTerm = safeNodeText(summaryLayers.get("shortTermSummary"));
        String mediumTerm = safeNodeText(summaryLayers.get("mediumTermSummary"));
        String longTerm = safeNodeText(summaryLayers.get("longTermSummary"));
        StringBuilder builder = new StringBuilder();
        appendLabeledLine(builder, "短期摘要", shortTerm);
        appendLabeledLine(builder, "中期摘要", mediumTerm);
        appendLabeledLine(builder, "长期摘要", longTerm);
        return builder.isEmpty() ? null : builder.toString();
    }

    /**
     * 返回压缩时必须保护的关键事实文本。
     *
     * @return 关键事实
     */
    public String protectedStateFactsText() {
        if (conversationStateContext == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendValueLines(builder, "已确认槽位", conversationStateContext.confirmedSlots().stream()
                .limit(8)
                .map(item -> item.label() + "=" + item.value())
                .toList());
        appendValueLines(builder, "已确认事实", conversationStateContext.confirmedFacts().stream().limit(8).toList());
        appendValueLines(builder, "偏好", conversationStateContext.preferences().stream().limit(6).toList());
        appendValueLines(builder, "约束", conversationStateContext.constraints().stream().limit(6).toList());
        appendValueLines(builder, "目标", conversationStateContext.goals().stream().limit(6).toList());
        return builder.isEmpty() ? null : builder.toString();
    }

    /**
     * 返回压缩与 Prompt 注入时使用的结构化保护事实。
     *
     * @return 结构化关键事实
     */
    public Map<String, Object> protectedStateFactsPayload() {
        if (conversationStateContext == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("confirmedSlots", conversationStateContext.confirmedSlots().stream()
                .limit(12)
                .map(item -> Map.<String, Object>of(
                        "key", safeValue(item.key()),
                        "label", safeValue(item.label()),
                        "value", safeValue(item.value()),
                        "valueType", safeValue(item.valueType()),
                        "required", item.required(),
                        "multiValue", item.multiValue(),
                        "confidence", item.confidence(),
                        "source", safeValue(item.source())
                ))
                .toList());
        payload.put("confirmedFacts", conversationStateContext.confirmedFacts().stream().limit(12).toList());
        payload.put("preferences", conversationStateContext.preferences().stream().limit(8).toList());
        payload.put("constraints", conversationStateContext.constraints().stream().limit(8).toList());
        payload.put("goals", conversationStateContext.goals().stream().limit(8).toList());
        return payload;
    }

    /**
     * 返回最近对话窗口文本。
     *
     * @return 最近对话窗口
     */
    public String recentConversationText() {
        if (recentConversationMessages == null || recentConversationMessages.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("最近对话窗口：");
        for (ConversationMessageContext messageContext : recentConversationMessages) {
            if (messageContext == null || messageContext.content() == null || messageContext.content().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(messageContext.sequenceNo() == null ? "" : messageContext.sequenceNo()).append(" ");
            builder.append("user".equalsIgnoreCase(messageContext.messageType()) ? "用户" : "助手");
            builder.append("：").append(messageContext.content());
        }
        return builder.length() == "最近对话窗口：".length() ? null : builder.toString();
    }

    /**
     * 返回当前会话槽位状态摘要文本。
     *
     * @return 会话槽位状态摘要
     */
    public String conversationStateText() {
        if (conversationStateContext == null) {
            return null;
        }
        String promptText = conversationStateContext.promptText();
        if (promptText == null || promptText.isBlank()) {
            return null;
        }
        return "会话状态：" + promptText.trim();
    }

    /**
     * 返回结构化会话状态载荷。
     *
     * @return 会话状态结构
     */
    public Map<String, Object> conversationStatePayload() {
        if (conversationStateContext == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", conversationStateContext.enabled());
        payload.put("summary", safeValue(conversationStateContext.summary()));
        payload.put("updatedAt", safeValue(conversationStateContext.updatedAt()));
        payload.put("schema", conversationStateContext.stateSchema().stream()
                .map(item -> Map.<String, Object>of(
                        "key", safeValue(item.key()),
                        "label", safeValue(item.label()),
                        "type", safeValue(item.type()),
                        "required", item.required(),
                        "multiValue", item.multiValue(),
                        "enumValues", item.enumValues() == null ? List.of() : item.enumValues(),
                        "description", safeValue(item.description()),
                        "overwritePolicy", safeValue(item.overwritePolicy()),
                        "conflictPolicy", safeValue(item.conflictPolicy())
                ))
                .toList());
        payload.put("confirmedSlots", conversationStateContext.confirmedSlots().stream()
                .map(item -> Map.<String, Object>of(
                        "key", safeValue(item.key()),
                        "label", safeValue(item.label()),
                        "value", safeValue(item.value()),
                        "valueType", safeValue(item.valueType()),
                        "required", item.required(),
                        "multiValue", item.multiValue(),
                        "confidence", item.confidence(),
                        "source", safeValue(item.source()),
                        "updatedAt", safeValue(item.updatedAt())
                ))
                .toList());
        payload.put("missingSlots", conversationStateContext.missingSlots().stream()
                .map(item -> Map.<String, Object>of(
                        "key", safeValue(item.key()),
                        "label", safeValue(item.label()),
                        "required", item.required(),
                        "reason", safeValue(item.reason())
                ))
                .toList());
        payload.put("confirmedFacts", conversationStateContext.confirmedFacts());
        payload.put("preferences", conversationStateContext.preferences());
        payload.put("constraints", conversationStateContext.constraints());
        payload.put("goals", conversationStateContext.goals());
        payload.put("openQuestions", conversationStateContext.openQuestions());
        payload.put("conflicts", conversationStateContext.conflicts().stream()
                .map(item -> Map.<String, Object>of(
                        "key", safeValue(item.key()),
                        "label", safeValue(item.label()),
                        "existingValue", safeValue(item.existingValue()),
                        "newValue", safeValue(item.newValue()),
                        "resolution", safeValue(item.resolution()),
                        "reason", safeValue(item.reason())
                ))
                .toList());
        return payload;
    }

    /**
     * 返回供 Prompt 注入的会话状态文本。
     *
     * @return 会话状态 Prompt 文本
     */
    public String conversationStatePromptText() {
        return conversationStateContext == null ? null : conversationStateContext.promptText();
    }

    /**
     * 返回当前用户消息内容。
     *
     * @return 用户消息
     */
    public String userMessage() {
        return messageContext.userMessage();
    }

    /**
     * 返回当前消息检索得到的独立知识上下文。
     *
     * @return 带引用编号的知识上下文
     */
    public String knowledgeContextText() {
        if (agentContext == null || agentContext.metadata() == null) {
            return null;
        }
        String value = agentContext.metadata().path("runtimeKnowledge").path("promptContext").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 返回当前有效 Tool 的展示标签。
     *
     * @return Tool 标签列表
     */
    public List<String> effectiveToolLabels() {
        return availableTools.stream()
                .map(item -> item.toolCode() + ":" + item.toolName())
                .toList();
    }

    /**
     * 返回当前可用 Workflow 的展示标签。
     *
     * @return Workflow 标签列表
     */
    public List<String> availableWorkflowLabels() {
        return availableWorkflows.stream()
                .map(item -> item.workflowCode() + ":" + item.workflowName())
                .toList();
    }

    /**
     * 返回当前可委派 Agent 的展示标签。
     *
     * @return Agent 标签列表
     */
    public List<String> availableDelegateLabels() {
        return availableDelegates.stream()
                .map(item -> item.agentCode() + ":" + item.agentName())
                .toList();
    }

    /**
     * 返回决策 Prompt 摘要文本。
     *
     * @return 决策 Prompt 摘要
     */
    public String decisionPromptSummary() {
        if (decisionPromptContext == null || decisionPromptContext.promptContent() == null) {
            return null;
        }
        String content = decisionPromptContext.promptContent().trim();
        if (content.isEmpty()) {
            return null;
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }

    /**
     * 返回规划 Prompt 摘要文本。
     *
     * @return 规划 Prompt 摘要
     */
    public String plannerPromptSummary() {
        if (plannerPromptContext == null || plannerPromptContext.promptContent() == null) {
            return null;
        }
        String content = plannerPromptContext.promptContent().trim();
        if (content.isEmpty()) {
            return null;
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }

    /**
     * 返回当前时间提示文本。
     *
     * @return 当前时间
     */
    public String currentTimeText() {
        if (timeContext == null) {
            return null;
        }
        return "当前本地时间：" + safeValue(timeContext.localDateTime())
                + "\n当前日期：" + safeValue(timeContext.currentDate())
                + "\n当前时区：" + safeValue(timeContext.zoneId());
    }

    /**
     * 判断当前模型是否可直接调用。
     *
     * @return 是否可调用
     */
    public boolean hasRunnableModel() {
        return isRunnableModel(modelContext);
    }

    /** 返回指定阶段模型，未配置时回退 Agent 默认模型。 */
    public ModelContext modelForStage(String stage) {
        if (stageModelContexts == null || stage == null || stage.isBlank()) {
            return modelContext;
        }
        return stageModelContexts.getOrDefault(stage.trim().toLowerCase(), modelContext);
    }

    /** 判断指定阶段是否有可执行模型。 */
    public boolean hasRunnableModel(String stage) {
        return isRunnableModel(modelForStage(stage));
    }

    private boolean isRunnableModel(ModelContext model) {
        return model != null
                && hasText(model.baseUrl())
                && hasText(model.apiKey())
                && hasText(model.modelIdentifier())
                && "enabled".equals(model.status());
    }

    /**
     * 返回当前用户记忆摘要文本。
     *
     * @return 用户记忆摘要
     */
    public String userMemoryText() {
        if (userMemoryContext == null) {
            return null;
        }
        String preferenceSummary = userMemoryContext.preferenceSummary();
        String attentionSummary = userMemoryContext.attentionSummary();
        String factSummary = userMemoryContext.factSummary();
        String goalSummary = userMemoryContext.goalSummary();
        if ((preferenceSummary == null || preferenceSummary.isBlank())
                && (attentionSummary == null || attentionSummary.isBlank())
                && (factSummary == null || factSummary.isBlank())
                && (goalSummary == null || goalSummary.isBlank())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (preferenceSummary != null && !preferenceSummary.isBlank()) {
            builder.append("用户偏好：").append(preferenceSummary);
        }
        if (attentionSummary != null && !attentionSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("用户关注点：").append(attentionSummary);
        }
        if (factSummary != null && !factSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("用户长期事实：").append(factSummary);
        }
        if (goalSummary != null && !goalSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("用户长期目标：").append(goalSummary);
        }
        return builder.toString();
    }

    /**
     * 返回最近一次任务记忆摘要文本。
     *
     * @return 最近任务记忆摘要
     */
    public String recentTaskMemoryText() {
        if (recentTaskMemoryContext == null) {
            return null;
        }
        String memorySummary = recentTaskMemoryContext.memorySummary();
        String keyFindings = recentTaskMemoryContext.keyFindings();
        if ((memorySummary == null || memorySummary.isBlank())
                && (keyFindings == null || keyFindings.isBlank())) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (memorySummary != null && !memorySummary.isBlank()) {
            builder.append("最近任务摘要：").append(memorySummary);
        }
        if (keyFindings != null && !keyFindings.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("最近关键发现：").append(keyFindings);
        }
        return builder.toString();
    }

    /**
     * 当前用户上下文。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param displayName 显示名称
     * @param roleCodes   角色编码列表
     * @param menuScopes  菜单范围列表
     * @param tenantId 租户 ID
     * @param organizationId 组织 ID
     * @param workspaceId 工作区 ID
     * @param projectId 项目 ID
     */
    public record CurrentUserContext(
            Long userId,
            String username,
            String displayName,
            List<String> roleCodes,
            List<String> menuScopes,
            Long tenantId,
            Long organizationId,
            Long workspaceId,
            Long projectId
    ) {
        /** 保留旧构造契约，旧调用方默认使用平台基础作用域。 */
        public CurrentUserContext(Long userId,
                                  String username,
                                  String displayName,
                                  List<String> roleCodes,
                                  List<String> menuScopes) {
            this(userId, username, displayName, roleCodes, menuScopes, 1L, 1L, 1L, 1L);
        }
    }

    /**
     * 会话记忆上下文。
     *
     * @param memorySummary        会话摘要
     * @param preferenceSummary    会话偏好摘要
     * @param lastUserMessage      最近用户消息
     * @param lastAssistantSummary 最近助手摘要
     */
    public record ConversationMemoryContext(
            String memorySummary,
            String preferenceSummary,
            String lastUserMessage,
            String lastAssistantSummary,
            JsonNode metadata
    ) {
    }

    /**
     * 用户记忆策略上下文。
     *
     * @param userMemoryEnabled 当前会话是否启用用户历史记忆
     * @param userMemoryMode 当前会话用户历史记忆模式
     * @param userMemoryWritebackEnabled 当前会话是否允许写回用户长期记忆
     * @param agentDefaultUserMemoryEnabled Agent 默认是否启用用户历史记忆
     * @param agentDefaultUserMemoryMode Agent 默认用户历史记忆模式
     * @param agentDefaultUserMemoryWritebackEnabled Agent 默认是否允许写回用户长期记忆
     */
    public record UserMemoryPolicyContext(
            boolean userMemoryEnabled,
            String userMemoryMode,
            boolean userMemoryWritebackEnabled,
            boolean agentDefaultUserMemoryEnabled,
            String agentDefaultUserMemoryMode,
            boolean agentDefaultUserMemoryWritebackEnabled
    ) {
    }

    /**
     * 会话槽位状态上下文。
     *
     * @param summary 状态摘要
     */
    public record ConversationStateContext(
            boolean enabled,
            String summary,
            String promptText,
            String updatedAt,
            List<StateSlotSchemaItem> stateSchema,
            List<StateSlotValue> confirmedSlots,
            List<StateMissingSlot> missingSlots,
            List<String> confirmedFacts,
            List<String> preferences,
            List<String> constraints,
            List<String> goals,
            List<String> openQuestions,
            List<StateConflictItem> conflicts
    ) {
    }

    /**
     * 会话状态槽位 schema。
     */
    public record StateSlotSchemaItem(
            String key,
            String label,
            String type,
            boolean required,
            boolean multiValue,
            List<String> enumValues,
            List<String> aliases,
            String extractionPattern,
            Integer extractionGroup,
            String description,
            String overwritePolicy,
            String conflictPolicy
    ) {
    }

    /**
     * 已确认槽位值。
     */
    public record StateSlotValue(
            String key,
            String label,
            String value,
            String valueType,
            boolean required,
            boolean multiValue,
            double confidence,
            String source,
            String updatedAt
    ) {
    }

    /**
     * 待补充槽位。
     */
    public record StateMissingSlot(
            String key,
            String label,
            boolean required,
            String reason
    ) {
    }

    /**
     * 冲突槽位。
     */
    public record StateConflictItem(
            String key,
            String label,
            String existingValue,
            String newValue,
            String resolution,
            String reason
    ) {
    }

    /**
     * 用户记忆上下文。
     *
     * @param preferenceSummary 用户偏好摘要
     * @param attentionSummary  用户关注点摘要
     * @param factSummary       用户长期事实摘要
     * @param goalSummary       用户长期目标摘要
     */
    public record UserMemoryContext(
            String preferenceSummary,
            String attentionSummary,
            String factSummary,
            String goalSummary
    ) {
    }

    /**
     * 最近一次任务记忆上下文。
     *
     * @param taskId                任务 ID
     * @param memorySummary         任务摘要
     * @param keyFindings           关键发现
     * @param finalResponseSummary  最终响应摘要
     */
    public record RecentTaskMemoryContext(
            Long taskId,
            String memorySummary,
            String keyFindings,
            String finalResponseSummary
    ) {
    }

    /**
     * 最近对话消息上下文。
     *
     * @param messageId   消息 ID
     * @param sequenceNo  消息序号
     * @param messageType 消息类型
     * @param content     消息内容
     */
    public record ConversationMessageContext(
            Long messageId,
            Integer sequenceNo,
            String messageType,
            String content
    ) {
    }

    /**
     * 会话上下文。
     *
     * @param conversationId   会话 ID
     * @param conversationCode 会话编码
     * @param title            会话标题
     * @param status           会话状态
     */
    public record ConversationContext(
            Long conversationId,
            String conversationCode,
            String title,
            String status,
            JsonNode metadata
    ) {
    }

    /**
     * Agent 上下文。
     *
     * @param agentId       Agent ID
     * @param agentCode     Agent 编码
     * @param agentName     Agent 名称
     * @param defaultModel  默认模型
     * @param agentStatus   Agent 状态
     * @param metadata      Agent 元数据
     */
    public record AgentContext(
            Long agentId,
            String agentCode,
            String agentName,
            String defaultModel,
            String agentStatus,
            JsonNode metadata
    ) {
    }

    /**
     * Prompt 上下文。
     *
     * @param promptId       Prompt ID
     * @param promptCode     Prompt 编码
     * @param promptName     Prompt 名称
     * @param promptType     Prompt 类型
     * @param promptCategory Prompt 分类
     * @param promptContent  Prompt 正文
     */
    public record PromptContext(
            Long promptId,
            String promptCode,
            String promptName,
            String promptType,
            String promptCategory,
            String promptContent
    ) {
    }

    /**
     * 当前时间上下文。
     *
     * @param localDateTime 本地日期时间
     * @param currentDate   当前日期
     * @param zoneId        当前时区
     */
    public record TimeContext(
            String localDateTime,
            String currentDate,
            String zoneId
    ) {
    }

    /**
     * 模型上下文。
     *
     * @param modelCode       模型编码
     * @param modelName       模型名称
     * @param provider        模型供应商
     * @param modelType       模型类型
     * @param baseUrl         基础地址
     * @param apiKey          API Key
     * @param apiPath         API Path
     * @param modelIdentifier 模型标识
     * @param temperature     温度
     * @param maxTokens       最大 Token 数
     * @param contextWindow   模型上下文窗口
     * @param reservedOutputTokens 预留输出 Token 数
     * @param reservedSystemTokens 预留系统 Token 数
     * @param compactionTriggerRatio 触发压缩比例
     * @param tokenizerFamily 分词器族
     * @param timeoutSeconds  超时时间（秒）
     * @param status          状态
     * @param metadata        模型能力元数据
     * @param callBudget      当前 Agent Run 共享的 LLM 调用预算
     */
    public record ModelContext(
            String modelCode,
            String modelName,
            String provider,
            String modelType,
            String baseUrl,
            String apiKey,
            String apiPath,
            String modelIdentifier,
            Double temperature,
            Integer maxTokens,
            Integer contextWindow,
            Integer reservedOutputTokens,
            Integer reservedSystemTokens,
            Double compactionTriggerRatio,
            String tokenizerFamily,
            Integer timeoutSeconds,
            String status,
            JsonNode metadata,
            LlmCallBudget callBudget
    ) {
        /** 保留旧模型上下文构造契约。 */
        public ModelContext(String modelCode, String modelName, String provider, String modelType,
                            String baseUrl, String apiKey, String apiPath, String modelIdentifier,
                            Double temperature, Integer maxTokens, Integer contextWindow,
                            Integer reservedOutputTokens, Integer reservedSystemTokens,
                            Double compactionTriggerRatio, String tokenizerFamily, Integer timeoutSeconds,
                            String status, LlmCallBudget callBudget) {
            this(modelCode, modelName, provider, modelType, baseUrl, apiKey, apiPath, modelIdentifier,
                    temperature, maxTokens, contextWindow, reservedOutputTokens, reservedSystemTokens,
                    compactionTriggerRatio, tokenizerFamily, timeoutSeconds, status, null, callBudget);
        }

        /** 判断模型声明是否支持指定输入模态，vision 类型默认支持 image。 */
        public boolean supportsInputModality(String modality) {
            if ("text".equalsIgnoreCase(modality)) return true;
            if ("image".equalsIgnoreCase(modality) && "vision".equalsIgnoreCase(modelType)) return true;
            JsonNode modalities = metadata == null ? null : metadata.path("inputModalities");
            if (modalities == null || !modalities.isArray()) return false;
            for (JsonNode item : modalities) {
                if (modality.equalsIgnoreCase(item.asText())) return true;
            }
            return false;
        }
    }

    /**
     * 消息上下文。
     *
     * @param userMessageId 当前用户消息 ID
     * @param userMessage   当前用户消息内容
     * @param attachments   当前消息附件引用
     */
    public record MessageContext(
            Long userMessageId,
            String userMessage,
            List<MessageAttachmentContext> attachments
    ) {
        /** 保留纯文本上下文的旧构造契约。 */
        public MessageContext(Long userMessageId, String userMessage) {
            this(userMessageId, userMessage, List.of());
        }
    }

    /**
     * 当前消息附件上下文，不包含文件正文和存储内部路径。
     * @param attachmentId 附件ID
     * @param attachmentCode 附件编码
     * @param fileName 原始文件名
     * @param mimeType 媒体类型
     * @param sizeBytes 文件大小
     * @param sha256 内容摘要
     */
    public record MessageAttachmentContext(
            Long attachmentId,
            String attachmentCode,
            String fileName,
            String mimeType,
            Long sizeBytes,
            String sha256
    ) {
    }

    /**
     * 任务上下文。
     *
     * @param taskId   任务 ID
     * @param taskCode 任务编码
     * @param traceId  任务 Trace ID
     */
    public record TaskContext(
            Long taskId,
            String taskCode,
            String traceId
    ) {
    }

    /**
     * 当前可委派 Agent 上下文。
     *
     * @param agentId      Agent ID
     * @param agentCode    Agent 编码
     * @param agentName    Agent 名称
     * @param description  Agent 描述
     */
    public record DelegateAgent(
            Long agentId,
            String agentCode,
            String agentName,
            String description
    ) {
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String safeNodeText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private void appendLabeledLine(StringBuilder builder, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append("：").append(value.trim());
    }

    private void appendValueLines(StringBuilder builder, String label, List<String> values) {
        if (values == null) {
            return;
        }
        List<String> normalized = values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append("：");
        for (String item : normalized) {
            builder.append("\n- ").append(item);
        }
    }
}
