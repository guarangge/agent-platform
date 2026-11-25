package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;

/** 从完整 ExecutionContext 中解析内核路由和执行所需的稳定运行上下文。 */
public interface RuntimeContextProvider {

    /** 解析当前执行作用域与主要资源标识。 */
    ResolvedRuntimeContext resolve(ExecutionContext executionContext);

    /**
     * @param executionContext 完整执行上下文
     * @param tenantId 租户 ID
     * @param organizationId 组织 ID
     * @param workspaceId 工作区 ID
     * @param projectId 项目 ID
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @param agentId Agent ID
     */
    record ResolvedRuntimeContext(
            ExecutionContext executionContext,
            Long tenantId,
            Long organizationId,
            Long workspaceId,
            Long projectId,
            Long userId,
            Long conversationId,
            Long agentId
    ) {
    }
}
