package com.zc.agent.core.modules.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.security.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将知识检索结果作为独立运行时上下文注入 Agent 执行现场。
 */
@Service
public class KnowledgeRuntimeContextService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRuntimeContextService.class);
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeRuntimeContextService(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /** 检索当前消息并将引用上下文挂载到 Agent metadata 的 runtimeKnowledge 节点。 */
    public ExecutionContext enrich(ExecutionContext context) {
        if (context == null || context.agentContext() == null || context.currentUser() == null
                || context.userMessage() == null || context.userMessage().isBlank()) {
            return context;
        }
        SecurityUser user = new SecurityUser(context.currentUser().userId(), context.currentUser().username(),
                context.currentUser().displayName(), context.currentUser().roleCodes(), context.currentUser().menuScopes());
        try {
            KnowledgeModels.SearchResponse response = retrievalService.search(new KnowledgeModels.SearchRequest(
                    context.agentContext().agentId(), null, context.userMessage(), null,
                    context.conversationContext() == null ? null : context.conversationContext().conversationId(),
                    context.taskContext() == null ? null : context.taskContext().taskId(),
                    context.taskContext() == null ? null : context.taskContext().traceId()), user);
            ObjectNode metadata = context.agentContext().metadata() != null && context.agentContext().metadata().isObject()
                    ? ((ObjectNode) context.agentContext().metadata()).deepCopy() : JsonNodeFactory.instance.objectNode();
            ObjectNode runtime = metadata.putObject("runtimeKnowledge");
            runtime.put("retrievalCode", response.retrievalCode());
            runtime.put("hitCount", response.hitCount());
            runtime.put("latencyMs", response.latencyMs());
            runtime.put("promptContext", response.promptContext());
            runtime.set("citations", com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(response.citations()));
            return copy(context, new ExecutionContext.AgentContext(context.agentContext().agentId(),
                    context.agentContext().agentCode(), context.agentContext().agentName(),
                    context.agentContext().defaultModel(), context.agentContext().agentStatus(), metadata));
        } catch (Exception exception) {
            // Knowledge 不可用时不阻断通用 Agent 主链，但保留日志供运行治理排查。
            log.warn("Knowledge 上下文构建失败，继续执行无知识上下文流程：agentId={}, reason={}",
                    context.agentContext().agentId(), exception.getMessage());
            return context;
        }
    }

    private ExecutionContext copy(ExecutionContext value, ExecutionContext.AgentContext agentContext) {
        return new ExecutionContext(value.currentUser(), value.conversationMemoryContext(), value.conversationStateContext(),
                value.userMemoryPolicyContext(), value.userMemoryContext(), value.recentTaskMemoryContext(),
                value.recentConversationMessages(), value.conversationContext(), agentContext, value.promptContext(),
                value.decisionPromptContext(), value.plannerPromptContext(), value.timeContext(), value.modelContext(), value.stageModelContexts(),
                value.messageContext(), value.taskContext(), value.availableTools(), value.availableWorkflows(),
                value.availableDelegates());
    }
}
