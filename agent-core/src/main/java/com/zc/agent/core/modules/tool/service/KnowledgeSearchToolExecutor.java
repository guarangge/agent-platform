package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.knowledge.service.KnowledgeRetrievalService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 平台知识库混合检索 Tool，沿用 Agent、角色与知识库权限校验。
 */
@Component
public class KnowledgeSearchToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "KNOWLEDGE_SEARCH";
    private final KnowledgeRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchToolExecutor(KnowledgeRetrievalService retrievalService, ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        String query = BuiltinToolSupport.text(request, "query", request.toolInputText());
        if (query == null || query.isBlank()) {
            throw BuiltinToolSupport.failure("知识检索问题不能为空", "KNOWLEDGE_QUERY_REQUIRED", Map.of());
        }
        Long knowledgeId = nullableLong(request.arguments() == null ? null : request.arguments().get("knowledgeId"));
        int topK = BuiltinToolSupport.integer(request, "topK", 5, 1, 50);
        ExecutionContext.CurrentUserContext currentUser = context.currentUser();
        SecurityUser user = new SecurityUser(currentUser.userId(), currentUser.username(), currentUser.displayName(),
                currentUser.roleCodes() == null ? List.of() : currentUser.roleCodes(),
                currentUser.menuScopes() == null ? List.of() : currentUser.menuScopes());
        KnowledgeModels.SearchResponse response = retrievalService.search(new KnowledgeModels.SearchRequest(
                context.agentContext().agentId(), knowledgeId, query.trim(), topK,
                context.conversationContext().conversationId(), context.taskContext().taskId(),
                context.taskContext().traceId()), user);
        String responseText = response.hitCount() == 0
                ? "知识库中未检索到匹配内容。"
                : response.promptContext();
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(),
                "知识检索完成，命中 " + response.hitCount() + " 条", responseText,
                BuiltinToolSupport.json(objectMapper, response));
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw BuiltinToolSupport.failure("knowledgeId 必须是整数", "TOOL_ARGUMENT_INVALID",
                    Map.of("argument", "knowledgeId"));
        }
    }
}
