package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

/**
 * 文档检索 Tool 执行器。
 */
@Component
public class DocSearchToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "DOC_SEARCH";

    private final PlatformDocSearchTool platformDocSearchTool;

    public DocSearchToolExecutor(PlatformDocSearchTool platformDocSearchTool) {
        this.platformDocSearchTool = platformDocSearchTool;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        return platformDocSearchTool.execute(executionContext, tool, toolInputText);
    }
}
