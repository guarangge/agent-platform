package com.zc.agent.core.modules.runtime.capability.model;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

/**
 * Capability Policy 决策输入。
 *
 * @param executionContext 当前执行上下文
 * @param tool 已完成生命周期路由的 Tool
 * @param invocation Tool 调用输入
 */
public record CapabilityPolicyRequest(
        ExecutionContext executionContext,
        RegistryAgentTool tool,
        ToolInvocationRequest invocation
) {
}
