package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

/**
 * 会话隔离持久文件工作区 Tool。
 */
@Component
public class WorkspaceFileToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "WORKSPACE_FILE";
    private final AgentWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public WorkspaceFileToolExecutor(AgentWorkspaceService workspaceService, ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String operation = BuiltinToolSupport.text(request, "operation", "list").toLowerCase();
        String path = BuiltinToolSupport.text(request, "path", ".");
        String content = BuiltinToolSupport.text(request, "content", "");
        boolean overwrite = Boolean.parseBoolean(BuiltinToolSupport.text(request, "overwrite", "false"));
        AgentWorkspaceService.WorkspaceResult result = workspaceService.execute(context, operation, path, content, overwrite);
        String responseText = "read".equals(operation) ? result.content()
                : BuiltinToolSupport.json(objectMapper, result);
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(),
                "工作区操作完成：" + operation, responseText, BuiltinToolSupport.json(objectMapper, result));
    }
}
