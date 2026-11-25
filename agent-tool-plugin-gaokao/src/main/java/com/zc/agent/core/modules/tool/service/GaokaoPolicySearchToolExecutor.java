package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

/**
 * 高考政策检索执行器。
 */
@Component
public class GaokaoPolicySearchToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "GAOKAO_POLICY_SEARCH";

    private final GaokaoPolicySearchTool gaokaoPolicySearchTool;

    public GaokaoPolicySearchToolExecutor(GaokaoPolicySearchTool gaokaoPolicySearchTool) {
        this.gaokaoPolicySearchTool = gaokaoPolicySearchTool;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        return gaokaoPolicySearchTool.execute(tool, toolInputText);
    }
}
