package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.sandbox.dto.SandboxInputFileRequest;
import com.zc.agent.core.modules.sandbox.service.SandboxService;
import com.zc.agent.core.modules.sandbox.vo.SandboxExecutionResponse;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Docker 强隔离代码和命令执行 Tool，复用 Sandbox 白名单、限额、审计与成本治理。
 */
@Component
public class SandboxExecuteToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "SANDBOX_EXECUTE";
    private final SandboxService sandboxService;
    private final ObjectMapper objectMapper;

    public SandboxExecuteToolExecutor(SandboxService sandboxService, ObjectMapper objectMapper) {
        this.sandboxService = sandboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String profileCode = BuiltinToolSupport.text(request, "profileCode", "DEFAULT_ISOLATED");
        List<String> command = BuiltinToolSupport.stringList(request, "command");
        if (command.isEmpty()) {
            throw BuiltinToolSupport.failure("Sandbox command 不能为空", "SANDBOX_COMMAND_REQUIRED", Map.of());
        }
        List<SandboxInputFileRequest> inputFiles = inputFiles(request.arguments() == null
                ? null : request.arguments().get("inputFiles"));
        List<String> artifactPaths = BuiltinToolSupport.stringList(request, "artifactPaths");
        ExecutionContext.CurrentUserContext currentUser = context.currentUser();
        SecurityUser user = new SecurityUser(currentUser.userId(), currentUser.username(), currentUser.displayName(),
                currentUser.roleCodes() == null ? List.of() : currentUser.roleCodes(),
                currentUser.menuScopes() == null ? List.of() : currentUser.menuScopes());
        SandboxExecutionResponse response = sandboxService.executeByProfileCode(profileCode, command,
                inputFiles, artifactPaths, user);
        String responseText = response.execution().stdoutText();
        if (responseText == null || responseText.isBlank()) {
            responseText = response.execution().stderrText();
        }
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(),
                "Sandbox 执行完成，状态：" + response.execution().status(), responseText == null ? "" : responseText,
                BuiltinToolSupport.json(objectMapper, response));
    }

    private List<SandboxInputFileRequest> inputFiles(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<SandboxInputFileRequest> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> map)) {
                throw BuiltinToolSupport.failure("inputFiles 元素必须是对象", "TOOL_ARGUMENT_INVALID",
                        Map.of("argument", "inputFiles"));
            }
            Object path = map.get("path");
            Object contentBase64 = map.get("contentBase64");
            if (path == null || contentBase64 == null) {
                throw BuiltinToolSupport.failure("inputFiles 必须包含 path 和 contentBase64", "TOOL_ARGUMENT_INVALID",
                        Map.of("argument", "inputFiles"));
            }
            result.add(new SandboxInputFileRequest(String.valueOf(path), String.valueOf(contentBase64)));
        }
        return List.copyOf(result);
    }
}
