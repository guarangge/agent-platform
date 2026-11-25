package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

import java.util.Map;

/**
 * 结构化内置 Tool 基类，同时保留文本调用兼容入口。
 */
abstract class StructuredToolExecutor implements ToolExecutor {

    @Override
    public final ToolExecutionResult execute(ExecutionContext executionContext,
                                             RegistryAgentTool tool,
                                             String toolInputText) {
        return execute(executionContext, tool, new ToolInvocationRequest(toolInputText, Map.of()));
    }

    @Override
    public abstract ToolExecutionResult execute(ExecutionContext executionContext,
                                                RegistryAgentTool tool,
                                                ToolInvocationRequest request);
}
