package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 发布不包含 Tool 参数值和输出正文的 Tool 调用生命周期事件。
 */
@Service
public class ToolRuntimeEventService {

    private static final Logger log = LoggerFactory.getLogger(ToolRuntimeEventService.class);
    private static final Pattern TOOL_CALL_ID_PATTERN = Pattern.compile("TOOL_CALL_[A-F0-9]{20}");
    private final RuntimeEventPublisher eventPublisher;

    public ToolRuntimeEventService(RuntimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** Tool 完成路由后发布请求事件，并返回本次调用的稳定关联上下文。 */
    public ToolCallEventContext requested(ExecutionContext executionContext,
                                          RegistryAgentTool tool,
                                          ToolInvocationRequest request) {
        return requested(executionContext, tool, request, null);
    }

    /** 使用调用方预先分配的 ID 发布请求事件，审批恢复时据此延续原 Tool Call。 */
    public ToolCallEventContext requested(ExecutionContext executionContext,
                                          RegistryAgentTool tool,
                                          ToolInvocationRequest request,
                                          String preferredToolCallId) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) {
            return ToolCallEventContext.inactive();
        }
        String toolCallId = preferredToolCallId != null
                && TOOL_CALL_ID_PATTERN.matcher(preferredToolCallId).matches()
                ? preferredToolCallId : newToolCallId();
        Long taskId = executionContext.taskContext() == null ? null : executionContext.taskContext().taskId();
        String traceId = executionContext.taskContext() == null ? null : executionContext.taskContext().traceId();
        ToolCallEventContext context = new ToolCallEventContext(
                runCode, toolCallId, tool.toolCode(), safe(tool.toolName()), safe(tool.providerType()),
                safe(tool.transportType()), taskId, traceId);
        LinkedHashMap<String, Object> payload = basePayload(context, "requested");
        payload.put("inputLength", request.toolInputText() == null ? 0 : request.toolInputText().length());
        payload.put("argumentNames", request.arguments() == null
                ? java.util.List.of()
                : new TreeSet<>(request.arguments().keySet()));
        eventPublisher.publishPayload(runCode, RuntimeEventType.TOOL_CALL_REQUESTED,
                payload, traceMetadata(context));
        return context;
    }

    /** 在策略决策前分配 Tool Call ID，供审批与后续 Tool 生命周期共用。 */
    public String newToolCallId() {
        return "TOOL_CALL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    /** Provider 真正执行前发布开始事件；写入失败时阻止尚未发生的外部调用。 */
    public void started(ToolCallEventContext context) {
        if (!context.active()) {
            return;
        }
        eventPublisher.publishPayload(context.runCode(), RuntimeEventType.TOOL_CALL_STARTED,
                basePayload(context, "running"), traceMetadata(context));
    }

    /** Provider 成功返回后发布完成事件，不保存原始结果内容。 */
    public void completed(ToolCallEventContext context, ToolExecutionResult result, long durationMs) {
        if (!context.active()) {
            return;
        }
        LinkedHashMap<String, Object> payload = basePayload(context, "completed");
        payload.put("durationMs", Math.max(0L, durationMs));
        payload.put("responseLength", result.responseText() == null ? 0 : result.responseText().length());
        payload.put("outputSnapshotLength", result.outputSnapshot() == null ? 0 : result.outputSnapshot().length());
        publishOutcome(context, RuntimeEventType.TOOL_CALL_COMPLETED, payload);
    }

    /** Tool 失败后发布安全错误分类，不写入原始异常正文。 */
    public void failed(ToolCallEventContext context, Throwable throwable, long durationMs) {
        if (!context.active()) {
            return;
        }
        LinkedHashMap<String, Object> payload = basePayload(context, "failed");
        payload.put("durationMs", Math.max(0L, durationMs));
        if (throwable instanceof ToolExecutionException toolException) {
            payload.put("errorCode", safe(toolException.errorCode()));
            payload.put("errorType", safe(toolException.errorType()));
            payload.put("retryable", toolException.retryable());
            if (toolException.providerStatusCode() != null) {
                payload.put("providerStatusCode", toolException.providerStatusCode());
            }
        } else {
            payload.put("errorCode", throwable.getClass().getSimpleName());
            payload.put("errorType", "runtime");
            payload.put("retryable", false);
        }
        publishOutcome(context, RuntimeEventType.TOOL_CALL_FAILED, payload);
    }

    private void publishOutcome(ToolCallEventContext context,
                                RuntimeEventType eventType,
                                Map<String, Object> payload) {
        try {
            eventPublisher.publishPayload(context.runCode(), eventType, payload, traceMetadata(context));
        } catch (RuntimeException exception) {
            // Provider 已经产生结果或失败，不能因结果事件写入异常而重复触发外部副作用。
            log.error("Tool 结果 Runtime Event 写入失败, runCode={}, toolCallId={}, eventType={}",
                    context.runCode(), context.toolCallId(), eventType.code(), exception);
        }
    }

    private LinkedHashMap<String, Object> basePayload(ToolCallEventContext context, String status) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", context.toolCallId());
        payload.put("toolCode", context.toolCode());
        payload.put("toolName", context.toolName());
        payload.put("providerType", context.providerType());
        payload.put("transportType", context.transportType());
        if (context.taskId() != null) {
            payload.put("taskId", context.taskId());
        }
        payload.put("status", status);
        return payload;
    }

    private Map<String, Object> traceMetadata(ToolCallEventContext context) {
        return context.traceId() == null || context.traceId().isBlank()
                ? Map.of()
                : Map.of("traceId", context.traceId());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param runCode Agent Run 编码
     * @param toolCallId Tool 调用稳定 ID
     * @param toolCode Tool 编码
     * @param toolName Tool 名称
     * @param providerType Provider 类型
     * @param transportType 传输类型
     * @param taskId Task ID
     * @param traceId Trace ID
     */
    public record ToolCallEventContext(
            String runCode,
            String toolCallId,
            String toolCode,
            String toolName,
            String providerType,
            String transportType,
            Long taskId,
            String traceId
    ) {
        private static ToolCallEventContext inactive() {
            return new ToolCallEventContext(null, null, null, null, null, null, null, null);
        }

        public boolean active() {
            return runCode != null && !runCode.isBlank();
        }
    }
}
