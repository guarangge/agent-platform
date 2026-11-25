package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Tool 调用过程写入 Trace。
 */
@Component
public class ToolTraceHook implements ToolInvocationHook {

    private final TaskTraceService taskTraceService;

    public ToolTraceHook(TaskTraceService taskTraceService) {
        this.taskTraceService = taskTraceService;
    }

    @Override
    public void beforeInvoke(ExecutionContext executionContext, RegistryAgentTool tool, ToolInvocationRequest request) {
        taskTraceService.recordSpan(
                executionContext.taskContext().taskId(),
                executionContext.taskContext().traceId(),
                null,
                "tool_invoke_prepare",
                "Tool 调用准备[" + tool.toolCode() + "]",
                "success",
                Map.of(
                        "toolCode", tool.toolCode(),
                        "providerType", tool.providerType() == null ? "" : tool.providerType(),
                        "arguments", request.arguments() == null ? Map.of() : request.arguments()
                ),
                Map.of("status", "prepared"),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                Map.of("hook", "before"),
                executionContext.currentUser().userId()
        );
    }

    @Override
    public void afterInvoke(ExecutionContext executionContext,
                            RegistryAgentTool tool,
                            ToolInvocationRequest request,
                            ToolExecutionResult result) {
        Map<String, Object> outputSnapshot = new LinkedHashMap<>();
        outputSnapshot.put("summary", result.summary());
        outputSnapshot.put("responseText", result.responseText());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("hook", "after");
        metadata.put("providerType", tool.providerType() == null ? "" : tool.providerType());
        metadata.put("transportType", tool.transportType() == null ? "" : tool.transportType());
        taskTraceService.recordSpan(
                executionContext.taskContext().taskId(),
                executionContext.taskContext().traceId(),
                null,
                "tool_invoke",
                "Tool 调用[" + tool.toolCode() + "]",
                "success",
                Map.of("toolCode", tool.toolCode(), "arguments", request.arguments() == null ? Map.of() : request.arguments()),
                outputSnapshot,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                metadata,
                executionContext.currentUser().userId()
        );
    }

    @Override
    public void onError(ExecutionContext executionContext,
                        RegistryAgentTool tool,
                        ToolInvocationRequest request,
                        ToolExecutionException exception) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("hook", "error");
        metadata.put("errorCode", exception.errorCode());
        metadata.put("errorType", exception.errorType());
        metadata.put("retryable", exception.retryable());
        metadata.put("providerStatusCode", exception.providerStatusCode());
        taskTraceService.recordSpan(
                executionContext.taskContext().taskId(),
                executionContext.taskContext().traceId(),
                null,
                "tool_invoke",
                "Tool 调用[" + tool.toolCode() + "]",
                "failed",
                Map.of("toolCode", tool.toolCode(), "arguments", request.arguments() == null ? Map.of() : request.arguments()),
                Map.of(),
                exception.getMessage(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                metadata,
                executionContext.currentUser().userId()
        );
    }
}
