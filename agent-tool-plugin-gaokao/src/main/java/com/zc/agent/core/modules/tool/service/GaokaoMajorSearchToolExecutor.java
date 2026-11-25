package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

/**
 * 高考专业检索执行器。
 */
@Component
public class GaokaoMajorSearchToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "GAOKAO_MAJOR_SEARCH";

    private final GaokaoMajorSearchTool gaokaoMajorSearchTool;

    public GaokaoMajorSearchToolExecutor(GaokaoMajorSearchTool gaokaoMajorSearchTool) {
        this.gaokaoMajorSearchTool = gaokaoMajorSearchTool;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        return gaokaoMajorSearchTool.execute(tool, toolInputText);
    }
}
