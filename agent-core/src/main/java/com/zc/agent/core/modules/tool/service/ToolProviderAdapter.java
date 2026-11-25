package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

/**
 * Tool Provider 适配器。
 */
public interface ToolProviderAdapter {

    /**
     * 是否支持当前 Provider 类型。
     *
     * @param providerType Provider 类型
     * @return 是否支持
     */
    boolean supports(String providerType);

    /**
     * 执行 Tool。
     *
     * @param executionContext 运行时上下文
     * @param tool             Tool 定义
     * @param request          调用请求
     * @return 执行结果
     */
    ToolExecutionResult execute(ExecutionContext executionContext,
                                RegistryAgentTool tool,
                                ToolInvocationRequest request);
}
