package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

/**
 * 本地内置 Tool Provider 适配器。
 */
@Component
public class InternalToolProviderAdapter implements ToolProviderAdapter {

    private final ToolExecutorRegistry toolExecutorRegistry;

    public InternalToolProviderAdapter(ToolExecutorRegistry toolExecutorRegistry) {
        this.toolExecutorRegistry = toolExecutorRegistry;
    }

    @Override
    public boolean supports(String providerType) {
        return providerType == null || providerType.isBlank() || "internal".equals(providerType);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        ToolExecutor toolExecutor = toolExecutorRegistry.resolve(tool.toolCode());
        try {
            return toolExecutor.execute(executionContext, tool, request);
        } catch (ToolExecutionException exception) {
            throw exception;
        } catch (BusinessException exception) {
            throw new ToolExecutionException(
                    exception.getMessage(),
                    "INTERNAL_TOOL_EXECUTION_FAILED",
                    "execution",
                    false,
                    null,
                    java.util.Map.of("toolCode", tool.toolCode())
            );
        }
    }
}
