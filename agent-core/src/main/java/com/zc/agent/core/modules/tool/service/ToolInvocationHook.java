package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

/**
 * Tool 调用 Hook。
 */
public interface ToolInvocationHook {

    void beforeInvoke(ExecutionContext executionContext, RegistryAgentTool tool, ToolInvocationRequest request);

    void afterInvoke(ExecutionContext executionContext, RegistryAgentTool tool, ToolInvocationRequest request, ToolExecutionResult result);

    void onError(ExecutionContext executionContext, RegistryAgentTool tool, ToolInvocationRequest request, ToolExecutionException exception);
}
