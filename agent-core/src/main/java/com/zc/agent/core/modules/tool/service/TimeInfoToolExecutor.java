package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

/**
 * 时间信息 Tool 执行器。
 */
@Component
public class TimeInfoToolExecutor implements ToolExecutor {

    private static final String TOOL_CODE = "TIME_INFO";

    private final TimeInfoTool timeInfoTool;

    public TimeInfoToolExecutor(TimeInfoTool timeInfoTool) {
        this.timeInfoTool = timeInfoTool;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       String toolInputText) {
        return timeInfoTool.execute(tool);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext executionContext,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        return timeInfoTool.execute(tool, BuiltinToolSupport.text(request, "zoneId", "Asia/Shanghai"));
    }
}
