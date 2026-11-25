package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.attachment.repository.ConversationAttachmentRepository;
import com.zc.agent.core.config.ConversationContextProperties;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.ConversationMemoryRecord;
import com.zc.agent.core.modules.conversation.model.ConversationRecord;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.memory.service.ConversationMemoryPolicyResolver;
import com.zc.agent.core.modules.memory.service.MemoryResolverService;
import com.zc.agent.core.modules.memory.service.ConversationStateService;
import com.zc.agent.core.modules.knowledge.service.KnowledgeRuntimeContextService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContextBuildOptions;
import com.zc.agent.core.modules.runtime.model.LlmCallBudget;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExecutionContext 构建器。
 */
@Service
public class ExecutionContextBuilder {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ConversationRepository conversationRepository;
    private final RegistryAgentClient registryAgentClient;
    private final MemoryResolverService memoryResolverService;
    private final ConversationStateService conversationStateService;
    private final ConversationContextProperties conversationContextProperties;
    private final KnowledgeRuntimeContextService knowledgeRuntimeContextService;
    private final PromptExperimentObservationService promptExperimentObservationService;
    private final ConversationAttachmentRepository attachmentRepository;

    @Autowired
    public ExecutionContextBuilder(ConversationRepository conversationRepository,
                                   RegistryAgentClient registryAgentClient,
                                   MemoryResolverService memoryResolverService,
                                   ConversationStateService conversationStateService,
                                   ConversationContextProperties conversationContextProperties,
                                   KnowledgeRuntimeContextService knowledgeRuntimeContextService,
                                   PromptExperimentObservationService promptExperimentObservationService,
                                   ConversationAttachmentRepository attachmentRepository) {
        this.conversationRepository = conversationRepository;
        this.registryAgentClient = registryAgentClient;
        this.memoryResolverService = memoryResolverService;
        this.conversationStateService = conversationStateService;
        this.conversationContextProperties = conversationContextProperties;
        this.knowledgeRuntimeContextService = knowledgeRuntimeContextService;
        this.promptExperimentObservationService = promptExperimentObservationService;
        this.attachmentRepository = attachmentRepository;
    }

    /** 保留原构造契约，测试场景不启用 Prompt 实验。 */
    public ExecutionContextBuilder(ConversationRepository conversationRepository,RegistryAgentClient registryAgentClient,
                                   MemoryResolverService memoryResolverService,ConversationStateService conversationStateService,
                                   ConversationContextProperties conversationContextProperties,KnowledgeRuntimeContextService knowledgeRuntimeContextService){
        this(conversationRepository,registryAgentClient,memoryResolverService,conversationStateService,conversationContextProperties,knowledgeRuntimeContextService,null,null);}

    /**
     * 构建一次执行所需的完整上下文。
     *
     * @param securityUser      当前登录用户
     * @param conversationRecord 当前会话记录
     * @param userMessageId     用户消息 ID
     * @param userMessage       用户消息内容
     * @param taskId            当前任务 ID
     * @param taskCode          当前任务编码
     * @param authorization     原始 Authorization 请求头
     * @return 运行时上下文
     */
    public ExecutionContext build(SecurityUser securityUser,
                                  ConversationRecord conversationRecord,
                                  Long userMessageId,
                                  String userMessage,
                                  Integer userSequenceNo,
                                  Long taskId,
                                  String taskCode,
                                  String traceId,
                                  String authorization) {
        return build(
                securityUser,
                conversationRecord,
                userMessageId,
                userMessage,
                userSequenceNo,
                taskId,
                taskCode,
                traceId,
                authorization,
                ExecutionContextBuildOptions.defaults()
        );
    }

    public ExecutionContext build(SecurityUser securityUser,
                                  ConversationRecord conversationRecord,
                                  Long userMessageId,
                                  String userMessage,
                                  Integer userSequenceNo,
                                  Long taskId,
                                  String taskCode,
                                  String traceId,
                                  String authorization,
                                  ExecutionContextBuildOptions options) {
        ExecutionContextBuildOptions resolvedOptions = options == null ? ExecutionContextBuildOptions.defaults() : options;
        ExecutionContext.CurrentUserContext currentUser = resolveCurrentUser(securityUser);
        ConversationMemoryRecord conversationMemoryRecord = resolvedOptions.skipConversationMemory()
                ? null
                : memoryResolverService.resolveConversationMemory(conversationRecord.conversationId()).orElse(null);
        ExecutionContext.ConversationMemoryContext conversationMemoryContext = conversationMemoryRecord == null
                ? null
                : resolveConversationMemoryContext(
                conversationMemoryRecord,
                userSequenceNo,
                resolvedOptions.skipConversationHistory()
        );
        ExecutionContext.ConversationContext conversationContext = resolveConversation(conversationRecord);
        AgentSnapshot agentSnapshot = resolveAgentSnapshot(conversationRecord, authorization);
        ExecutionContext.UserMemoryPolicyContext userMemoryPolicyContext = resolveUserMemoryPolicyContext(conversationRecord, agentSnapshot);
        ExecutionContext.UserMemoryContext userMemoryContext = resolvedOptions.skipUserMemory()
                ? null
                : resolveUserMemoryContext(securityUser.userId(), conversationRecord.agentId(), userMemoryPolicyContext);
        ExecutionContext.RecentTaskMemoryContext recentTaskMemoryContext = resolvedOptions.skipRecentTaskMemory()
                ? null
                : resolveRecentTaskMemoryContext(conversationRecord.conversationId(), taskId);
        ExecutionContext.ConversationStateContext conversationStateContext = resolvedOptions.skipConversationMemory()
                ? null
                : conversationStateService.resolveConversationStateContext(agentSnapshot.metadata(),
                conversationMemoryRecord == null ? null : conversationMemoryRecord.metadata());
        ExecutionContext.AgentContext agentContext = resolveAgentContext(agentSnapshot);
        ExecutionContext.PromptContext promptContext = resolveExperimentPrompt(agentSnapshot.defaultPromptId(),"system",securityUser,conversationRecord,agentSnapshot,taskId,authorization);
        ExecutionContext.PromptContext decisionPromptContext = resolveExperimentPrompt(agentSnapshot.decisionPromptId(),"decision",securityUser,conversationRecord,agentSnapshot,taskId,authorization);
        ExecutionContext.PromptContext plannerPromptContext = resolveExperimentPrompt(agentSnapshot.plannerPromptId(),"planner",securityUser,conversationRecord,agentSnapshot,taskId,authorization);
        ExecutionContext.TimeContext timeContext = resolveTimeContext();
        LlmCallBudget callBudget = new LlmCallBudget(resolveMaxLlmCallsPerRun(agentSnapshot));
        ExecutionContext.ModelContext modelContext = withLlmCallBudget(resolveModelSnapshot(agentSnapshot, authorization), callBudget);
        Map<String, ExecutionContext.ModelContext> stageModelContexts = resolveStageModelSnapshots(
                agentSnapshot, authorization, modelContext, callBudget);
        ExecutionContext.MessageContext messageContext = resolveMessageContext(
                conversationRecord.conversationId(), userMessageId, userMessage);
        ExecutionContext.TaskContext taskContext = resolveTaskContext(taskId, taskCode, traceId);
        List<ExecutionContext.ConversationMessageContext> recentConversationMessages = resolvedOptions.skipConversationHistory()
                ? List.of()
                : resolveRecentConversationMessages(conversationRecord.conversationId(), userSequenceNo);
        List<com.fasterxml.jackson.databind.JsonNode> effectiveGuardrailPolicies = registryAgentClient.getEffectiveGuardrailPolicies(authorization);
        agentContext = registryAgentClient.attachGuardrailPoliciesToAgent(agentContext, effectiveGuardrailPolicies);
        List<RegistryAgentTool> availableTools = registryAgentClient.attachGuardrailPolicies(
                resolveAvailableTools(conversationRecord.agentId(), authorization),
                effectiveGuardrailPolicies
        );
        List<RegistryAgentWorkflow> availableWorkflows = registryAgentClient.attachGuardrailPoliciesToWorkflows(
                resolveAvailableWorkflows(conversationRecord.agentId(), authorization),
                effectiveGuardrailPolicies
        );
        List<ExecutionContext.DelegateAgent> availableDelegates = resolveAvailableDelegates(conversationRecord.agentId(), authorization);
        ExecutionContext executionContext = new ExecutionContext(
                currentUser,
                conversationMemoryContext,
                conversationStateContext,
                userMemoryPolicyContext,
                userMemoryContext,
                recentTaskMemoryContext,
                recentConversationMessages,
                conversationContext,
                agentContext,
                promptContext,
                decisionPromptContext,
                plannerPromptContext,
                timeContext,
                modelContext,
                stageModelContexts,
                messageContext,
                taskContext,
                availableTools,
                availableWorkflows,
                availableDelegates
        );
        return knowledgeRuntimeContextService.enrich(executionContext);
    }

    private ExecutionContext.CurrentUserContext resolveCurrentUser(SecurityUser securityUser) {
        return new ExecutionContext.CurrentUserContext(
                securityUser.userId(),
                securityUser.username(),
                securityUser.displayName(),
                securityUser.roleCodes(),
                securityUser.menuScopes(),
                securityUser.tenantId(),
                securityUser.organizationId(),
                securityUser.workspaceId(),
                securityUser.projectId()
        );
    }

    private ExecutionContext.ConversationContext resolveConversation(ConversationRecord conversationRecord) {
        return new ExecutionContext.ConversationContext(
                conversationRecord.conversationId(),
                conversationRecord.conversationCode(),
                conversationRecord.title(),
                conversationRecord.status(),
                conversationRecord.metadata()
        );
    }

    private ExecutionContext.ConversationMemoryContext resolveConversationMemoryContext(ConversationMemoryRecord conversationMemoryRecord,
                                                                                        Integer userSequenceNo,
                                                                                        boolean skipConversationHistory) {
        Long conversationId = conversationMemoryRecord.conversationId();
        ExecutionContext.ConversationMemoryContext baseContext = new ExecutionContext.ConversationMemoryContext(
                sanitizeConversationMemorySummary(conversationMemoryRecord.memorySummary()),
                conversationMemoryRecord.preferenceSummary(),
                conversationMemoryRecord.lastUserMessage(),
                isRuntimeControlAssistantContent(conversationMemoryRecord.lastAssistantSummary())
                        ? null
                        : conversationMemoryRecord.lastAssistantSummary(),
                conversationMemoryRecord.metadata()
        );
        if (skipConversationHistory || userSequenceNo == null || userSequenceNo <= 1) {
            return baseContext;
        }
        return new ExecutionContext.ConversationMemoryContext(
                baseContext == null ? null : baseContext.memorySummary(),
                baseContext == null ? null : baseContext.preferenceSummary(),
                baseContext == null ? null : baseContext.lastUserMessage(),
                baseContext == null ? null : baseContext.lastAssistantSummary(),
                baseContext == null ? null : baseContext.metadata()
        );
    }

    private ExecutionContext.UserMemoryContext resolveUserMemoryContext(Long userId,
                                                                        Long agentId,
                                                                        ExecutionContext.UserMemoryPolicyContext policyContext) {
        if (policyContext == null || !policyContext.userMemoryEnabled()) {
            return null;
        }
        return memoryResolverService.resolveUserMemoryContext(userId, agentId, policyContext.userMemoryMode());
    }

    private ExecutionContext.RecentTaskMemoryContext resolveRecentTaskMemoryContext(Long conversationId, Long taskId) {
        return memoryResolverService.resolveRecentTaskMemoryContext(conversationId, taskId);
    }

    private List<ExecutionContext.ConversationMessageContext> resolveRecentConversationMessages(Long conversationId,
                                                                                                Integer userSequenceNo) {
        if (userSequenceNo == null || userSequenceNo <= 2) {
            return List.of();
        }
        int recentTurnCount = Math.max(0, conversationContextProperties.getRecentConversationTurns());
        if (recentTurnCount <= 0) {
            return List.of();
        }
        int maxMessages = recentTurnCount * 2;
        int historyExclusiveSequenceNo = userSequenceNo;
        List<MessageRecord> recentMessages = conversationRepository.findRecentMessagesByConversationIdBeforeSequenceNo(
                        conversationId,
                        historyExclusiveSequenceNo,
                        maxMessages * 3
                ).stream()
                .filter(message -> !isRuntimeControlAssistantMessage(message))
                .limit(maxMessages)
                .toList();
        if (recentMessages.isEmpty()) {
            return List.of();
        }
        ArrayList<ExecutionContext.ConversationMessageContext> contexts = new ArrayList<>();
        ArrayList<MessageRecord> reversedMessages = new ArrayList<>(recentMessages);
        Collections.reverse(reversedMessages);
        for (MessageRecord record : reversedMessages) {
            contexts.add(new ExecutionContext.ConversationMessageContext(
                    record.messageId(),
                    record.sequenceNo(),
                    record.messageType(),
                    truncateMessage(record.content())
            ));
        }
        return contexts;
    }

    /** 过滤历史遗留的运行控制提示，避免审批/暂停状态继续污染后续模型上下文。 */
    private boolean isRuntimeControlAssistantMessage(MessageRecord message) {
        if (!"assistant".equals(message.messageType()) || message.content() == null) {
            return false;
        }
        return isRuntimeControlAssistantContent(message.content());
    }

    private boolean isRuntimeControlAssistantContent(String messageContent) {
        if (messageContent == null) {
            return false;
        }
        String content = messageContent.trim();
        return (content.contains("审批节点") && content.contains("等待人工审批"))
                || "当前任务等待审批".equals(content)
                || (content.startsWith("审批节点「")
                && (content.contains("当前任务结束") || content.contains("二次规划")))
                || (content.startsWith("# 当前进度汇报")
                && content.contains("高风险Tool人工审批")
                && (content.contains("等待中") || content.contains("暂停")))
                || (content.contains("### 可下载产物")
                && (content.contains("给不出下载链接")
                || content.contains("可提供的下载链接 | ❌")
                || content.contains("对外可下载的 PDF 产物 | ❌")))
                || content.contains("任务已在安全步骤边界暂停")
                || content.contains("任务等待外部事件");
    }

    private String sanitizeConversationMemorySummary(String memorySummary) {
        if (memorySummary == null || memorySummary.isBlank()) {
            return memorySummary;
        }
        return memorySummary.lines()
                .filter(line -> !(line.contains("最近助手结论") && isContaminatedAssistantSummary(line)))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private boolean isContaminatedAssistantSummary(String content) {
        return isRuntimeControlAssistantContent(content)
                || (content.contains("高风险Tool人工审批")
                && (content.contains("暂停")
                || content.contains("等待")
                || content.contains("被拒绝")
                || content.contains("未通过")))
                || content.contains("给不出下载链接")
                || content.contains("没能把可下载的 PDF 交付");
    }

    private String truncateMessage(String content) {
        if (content == null) {
            return null;
        }
        int maxLength = Math.max(1, conversationContextProperties.getRecentConversationMessageMaxLength());
        if (content.length() <= maxLength) {
            return content;
        }
        if (maxLength <= 3) {
            return content.substring(0, maxLength);
        }
        return content.substring(0, maxLength - 3) + "...";
    }

    private AgentSnapshot resolveAgentSnapshot(ConversationRecord conversationRecord, String authorization) {
        AgentSnapshot runtimeSnapshot = null;
        if (StringUtils.hasText(authorization)) {
            try {
                runtimeSnapshot = registryAgentClient.getEnabledAgentSnapshot(conversationRecord.agentId(), authorization);
            } catch (Exception ignored) {
                runtimeSnapshot = null;
            }
        }
        return new AgentSnapshot(
                conversationRecord.agentId(),
                conversationRecord.agentCode(),
                conversationRecord.agentName(),
                conversationRecord.defaultModel(),
                conversationRecord.defaultPromptId(),
                conversationRecord.defaultPromptName(),
                conversationRecord.decisionPromptId(),
                conversationRecord.decisionPromptName(),
                conversationRecord.plannerPromptId(),
                conversationRecord.plannerPromptName(),
                runtimeSnapshot == null ? conversationRecord.status() : runtimeSnapshot.status(),
                runtimeSnapshot == null ? null : runtimeSnapshot.metadata()
        );
    }

    private ExecutionContext.UserMemoryPolicyContext resolveUserMemoryPolicyContext(ConversationRecord conversationRecord,
                                                                                    AgentSnapshot agentSnapshot) {
        ConversationMemoryPolicyResolver.ConversationUserMemoryPolicy policy = ConversationMemoryPolicyResolver.resolveConversationPolicy(
                conversationRecord.metadata(),
                agentSnapshot == null ? null : agentSnapshot.metadata()
        );
        return new ExecutionContext.UserMemoryPolicyContext(
                policy.userMemoryEnabled(),
                policy.userMemoryMode(),
                policy.userMemoryWritebackEnabled(),
                policy.agentDefaultUserMemoryEnabled(),
                policy.agentDefaultUserMemoryMode(),
                policy.agentDefaultUserMemoryWritebackEnabled()
        );
    }

    private ExecutionContext.AgentContext resolveAgentContext(AgentSnapshot agentSnapshot) {
        return new ExecutionContext.AgentContext(
                agentSnapshot.agentId(),
                agentSnapshot.agentCode(),
                agentSnapshot.agentName(),
                agentSnapshot.defaultModel(),
                agentSnapshot.status(),
                agentSnapshot.metadata()
        );
    }

    private ExecutionContext.PromptContext resolveExperimentPrompt(Long basePromptId,String stage,SecurityUser user,
                                                                    ConversationRecord conversation,AgentSnapshot agent,
                                                                    Long taskId,String authorization){
        if(basePromptId==null){String name="decision".equals(stage)?agent.decisionPromptName():"planner".equals(stage)?agent.plannerPromptName():agent.defaultPromptName();return new ExecutionContext.PromptContext(null,null,name,null,null,null);}
        try{
            var selected=registryAgentClient.resolvePromptExperiment(basePromptId,user.userId(),conversation.conversationId(),agent.agentId(),stage,authorization);
            if(selected.isPresent()){
                var value=selected.get();
                if(promptExperimentObservationService!=null)promptExperimentObservationService.expose(value,user.userId(),agent.agentId(),conversation.conversationId(),taskId);
                return new ExecutionContext.PromptContext(value.selectedPromptId(),value.promptCode(),value.promptName(),value.promptType(),value.promptCategory(),value.content());
            }
        }catch(Exception exception){org.slf4j.LoggerFactory.getLogger(ExecutionContextBuilder.class).warn("Prompt 实验解析失败，回退基础 Prompt，basePromptId={}, stage={}, message={}",basePromptId,stage,exception.getMessage());}
        return registryAgentClient.getPromptSnapshot(basePromptId,authorization);
    }

    private ExecutionContext.PromptContext resolvePromptSnapshot(AgentSnapshot agentSnapshot, String authorization) {
        if (agentSnapshot.defaultPromptId() == null) {
            return new ExecutionContext.PromptContext(
                    null,
                    null,
                    agentSnapshot.defaultPromptName(),
                    null,
                    null,
                    null
            );
        }
        return registryAgentClient.getPromptSnapshot(agentSnapshot.defaultPromptId(), authorization);
    }

    private ExecutionContext.PromptContext resolveDecisionPromptSnapshot(AgentSnapshot agentSnapshot, String authorization) {
        if (agentSnapshot.decisionPromptId() == null) {
            return new ExecutionContext.PromptContext(
                    null,
                    null,
                    agentSnapshot.decisionPromptName(),
                    null,
                    null,
                    null
            );
        }
        return registryAgentClient.getPromptSnapshot(agentSnapshot.decisionPromptId(), authorization);
    }

    private ExecutionContext.PromptContext resolvePlannerPromptSnapshot(AgentSnapshot agentSnapshot, String authorization) {
        if (agentSnapshot.plannerPromptId() == null) {
            return new ExecutionContext.PromptContext(
                    null,
                    null,
                    agentSnapshot.plannerPromptName(),
                    null,
                    null,
                    null
            );
        }
        return registryAgentClient.getPromptSnapshot(agentSnapshot.plannerPromptId(), authorization);
    }

    private ExecutionContext.TimeContext resolveTimeContext() {
        ZoneId zoneId = PLATFORM_ZONE_ID;
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return new ExecutionContext.TimeContext(
                now.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                now.toLocalDate().toString(),
                zoneId.toString()
        );
    }

    private ExecutionContext.ModelContext resolveModelSnapshot(AgentSnapshot agentSnapshot, String authorization) {
        if (agentSnapshot.defaultModel() == null || agentSnapshot.defaultModel().isBlank()) {
            return null;
        }
        return registryAgentClient.getModelSnapshot(agentSnapshot.defaultModel(), authorization);
    }

    private int resolveMaxLlmCallsPerRun(AgentSnapshot agentSnapshot) {
        JsonNode value = agentSnapshot.metadata() == null
                ? null : agentSnapshot.metadata().path("executionPolicy").path("maxLlmCallsPerRun");
        int configured = value == null || !value.canConvertToInt() ? 6 : value.asInt();
        return Math.max(1, Math.min(50, configured));
    }

    private Map<String, ExecutionContext.ModelContext> resolveStageModelSnapshots(AgentSnapshot agentSnapshot,
                                                                                  String authorization,
                                                                                  ExecutionContext.ModelContext defaultModel,
                                                                                  LlmCallBudget callBudget) {
        JsonNode configured = agentSnapshot.metadata() == null
                ? null : agentSnapshot.metadata().path("executionPolicy").path("stageModels");
        if (configured == null || !configured.isObject()) {
            return Map.of();
        }
        Map<String, ExecutionContext.ModelContext> models = new LinkedHashMap<>();
        for (String stage : List.of("decision", "planning", "reply", "reflection", "memory", "delegation")) {
            String modelCode = configured.path(stage).asText("").trim();
            if (modelCode.isBlank()) {
                continue;
            }
            ExecutionContext.ModelContext selected = defaultModel != null && modelCode.equals(defaultModel.modelCode())
                    ? defaultModel
                    : withLlmCallBudget(registryAgentClient.getModelSnapshot(modelCode, authorization), callBudget);
            models.put(stage, selected);
        }
        return Map.copyOf(models);
    }

    private ExecutionContext.ModelContext withLlmCallBudget(ExecutionContext.ModelContext modelContext,
                                                             LlmCallBudget callBudget) {
        if (modelContext == null) {
            return null;
        }
        return new ExecutionContext.ModelContext(
                modelContext.modelCode(), modelContext.modelName(), modelContext.provider(), modelContext.modelType(),
                modelContext.baseUrl(), modelContext.apiKey(), modelContext.apiPath(), modelContext.modelIdentifier(),
                modelContext.temperature(), modelContext.maxTokens(), modelContext.contextWindow(),
                modelContext.reservedOutputTokens(), modelContext.reservedSystemTokens(),
                modelContext.compactionTriggerRatio(), modelContext.tokenizerFamily(), modelContext.timeoutSeconds(),
                modelContext.status(), modelContext.metadata(), callBudget
        );
    }

    private ExecutionContext.MessageContext resolveMessageContext(Long conversationId,
                                                                  Long userMessageId,
                                                                  String userMessage) {
        List<ExecutionContext.MessageAttachmentContext> attachments = attachmentRepository == null
                ? List.of()
                : attachmentRepository.findReadyByMessageId(conversationId, userMessageId).stream()
                .map(record -> new ExecutionContext.MessageAttachmentContext(
                        record.attachmentId(), record.attachmentCode(), record.originalFilename(),
                        record.mimeType(), record.sizeBytes(), record.sha256()))
                .toList();
        return new ExecutionContext.MessageContext(userMessageId, userMessage, attachments);
    }

    private ExecutionContext.TaskContext resolveTaskContext(Long taskId, String taskCode, String traceId) {
        return new ExecutionContext.TaskContext(taskId, taskCode, traceId);
    }

    private List<RegistryAgentTool> resolveAvailableTools(Long agentId, String authorization) {
        return registryAgentClient.getEffectiveTools(agentId, authorization);
    }

    private List<RegistryAgentWorkflow> resolveAvailableWorkflows(Long agentId, String authorization) {
        // ExecutionContext 当前仅承载 Workflow 候选列表，完整 Workflow 定义在命中后执行时按需加载。
        return registryAgentClient.getAvailableWorkflows(agentId, authorization);
    }

    private List<ExecutionContext.DelegateAgent> resolveAvailableDelegates(Long currentAgentId, String authorization) {
        return registryAgentClient.getEnabledAgents(authorization).stream()
                .filter(item -> !item.agentId().equals(currentAgentId))
                .toList();
    }
}
