package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

/** 基于已冻结 ExecutionContext 的默认运行上下文解析器。 */
@Service
public class DefaultRuntimeContextProvider implements RuntimeContextProvider {

    @Override
    public ResolvedRuntimeContext resolve(ExecutionContext executionContext) {
        ExecutionContext.CurrentUserContext currentUser = executionContext == null
                ? null
                : executionContext.currentUser();
        ExecutionContext.ConversationContext conversation = executionContext == null
                ? null
                : executionContext.conversationContext();
        ExecutionContext.AgentContext agent = executionContext == null
                ? null
                : executionContext.agentContext();
        return new ResolvedRuntimeContext(
                executionContext,
                currentUser == null ? null : currentUser.tenantId(),
                currentUser == null ? null : currentUser.organizationId(),
                currentUser == null ? null : currentUser.workspaceId(),
                currentUser == null ? null : currentUser.projectId(),
                currentUser == null ? null : currentUser.userId(),
                conversation == null ? null : conversation.conversationId(),
                agent == null ? null : agent.agentId()
        );
    }
}
