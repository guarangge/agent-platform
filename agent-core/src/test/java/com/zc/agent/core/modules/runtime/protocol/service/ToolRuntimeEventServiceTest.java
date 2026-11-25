package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class ToolRuntimeEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishSafeToolLifecycleWithoutArgumentValues() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ToolRuntimeEventService service = new ToolRuntimeEventService(publisher);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.taskContext()).thenReturn(new ExecutionContext.TaskContext(12L, "TASK_1", "TRACE_1"));
        AgentRunStreamingContext.bind("RUN_1");

        ToolRuntimeEventService.ToolCallEventContext context = service.requested(
                executionContext,
                tool(),
                new ToolInvocationRequest("查询北京天气", Map.of("city", "北京", "apiKey", "secret-value"))
        );
        service.started(context);
        service.completed(context,
                new ToolExecutionResult("WEATHER", "天气查询", "完成", "晴", "{\"temperature\":26}"), 18L);

        assertThat(context.toolCallId()).startsWith("TOOL_CALL_");
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_REQUESTED),
                argThat(payload -> payload.get("toolCallId").equals(context.toolCallId())
                        && payload.get("argumentNames").toString().contains("city")
                        && !payload.toString().contains("secret-value")
                        && !payload.toString().contains("北京")),
                eq(Map.of("traceId", "TRACE_1")));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_STARTED),
                argThat(payload -> "running".equals(payload.get("status"))),
                eq(Map.of("traceId", "TRACE_1")));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_COMPLETED),
                argThat(payload -> "completed".equals(payload.get("status"))
                        && Long.valueOf(18L).equals(payload.get("durationMs"))),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldPublishSafeFailureClassification() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ToolRuntimeEventService service = new ToolRuntimeEventService(publisher);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.taskContext()).thenReturn(new ExecutionContext.TaskContext(12L, "TASK_1", "TRACE_1"));
        AgentRunStreamingContext.bind("RUN_1");
        var context = service.requested(executionContext, tool(), new ToolInvocationRequest("", Map.of()));

        service.failed(context, new ToolExecutionException(
                "包含供应商敏感正文", "TOOL_TIMEOUT", "timeout", true, 504, Map.of()), 25L);

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_FAILED),
                argThat(payload -> "TOOL_TIMEOUT".equals(payload.get("errorCode"))
                        && Boolean.TRUE.equals(payload.get("retryable"))
                        && !payload.toString().contains("包含供应商敏感正文")),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldReusePreferredToolCallIdAfterApproval() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ToolRuntimeEventService service = new ToolRuntimeEventService(publisher);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.taskContext()).thenReturn(new ExecutionContext.TaskContext(12L, "TASK_1", "TRACE_1"));
        AgentRunStreamingContext.bind("RUN_1");

        var context = service.requested(
                executionContext,
                tool(),
                new ToolInvocationRequest("", Map.of()),
                "TOOL_CALL_1234567890ABCDEF1234");

        assertThat(context.toolCallId()).isEqualTo("TOOL_CALL_1234567890ABCDEF1234");
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_REQUESTED),
                argThat(payload -> "TOOL_CALL_1234567890ABCDEF1234".equals(payload.get("toolCallId"))),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldNotRepeatCompletedProviderCallWhenOutcomeEventFails() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ToolRuntimeEventService service = new ToolRuntimeEventService(publisher);
        var context = new ToolRuntimeEventService.ToolCallEventContext(
                "RUN_1", "TOOL_CALL_1", "WEATHER", "天气查询", "internal", "local", 12L, "TRACE_1");
        doThrow(new IllegalStateException("event store unavailable"))
                .when(publisher).publishPayload(
                        eq("RUN_1"), eq(RuntimeEventType.TOOL_CALL_COMPLETED),
                        org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap());

        assertThatCode(() -> service.completed(context,
                new ToolExecutionResult("WEATHER", "天气查询", "完成", "晴", "{}"), 10L))
                .doesNotThrowAnyException();
    }

    private RegistryAgentTool tool() {
        return new RegistryAgentTool(
                1L, "WEATHER", "天气查询", "function", "general", null, null,
                "internal", "local", null, null, null, null, null,
                null, null, null, null, 30, 0, null, null, null, null, null,
                "low", 30, "enabled", "", null
        );
    }
}
