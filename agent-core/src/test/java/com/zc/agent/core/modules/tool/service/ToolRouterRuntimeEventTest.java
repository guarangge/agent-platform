package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyEngine;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyRuntimeEventService;
import com.zc.agent.core.modules.runtime.protocol.service.ToolRuntimeEventService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolRouterRuntimeEventTest {

    private ToolProviderAdapter providerAdapter;
    private ToolRuntimeEventService eventService;
    private ToolRouterService routerService;
    private ExecutionContext executionContext;
    private RegistryAgentTool tool;
    private ToolInvocationRequest request;
    private ToolRuntimeEventService.ToolCallEventContext eventContext;

    @BeforeEach
    void setUp() {
        providerAdapter = mock(ToolProviderAdapter.class);
        eventService = mock(ToolRuntimeEventService.class);
        executionContext = mock(ExecutionContext.class);
        when(executionContext.taskContext()).thenReturn(new ExecutionContext.TaskContext(12L, "TASK_1", "TRACE_1"));
        tool = tool();
        request = new ToolInvocationRequest("输入", Map.of());
        eventContext = new ToolRuntimeEventService.ToolCallEventContext(
                "RUN_1", "TOOL_CALL_1", "WEATHER", "天气查询", "internal", "local", 12L, "TRACE_1");
        when(eventService.requested(executionContext, tool, request, null)).thenReturn(eventContext);
        when(providerAdapter.supports("internal")).thenReturn(true);
        routerService = new ToolRouterService(
                List.of(providerAdapter),
                List.of(),
                mock(ToolSchemaValidationService.class),
                mock(ToolSelectionService.class),
                mock(ToolLifecycleRoutingService.class),
                mock(ToolCircuitBreakerService.class),
                mock(ToolFallbackPolicyService.class),
                new ObjectMapper(),
                ObservationRegistry.NOOP,
                mock(CostGovernanceService.class),
                mock(ToolSideEffectService.class),
                eventService,
                mock(CapabilityPolicyEngine.class),
                mock(CapabilityPolicyRuntimeEventService.class)
        );
    }

    @Test
    void shouldPublishStartedAndCompletedAroundProviderCall() {
        ToolExecutionResult result = new ToolExecutionResult(
                "WEATHER", "天气查询", "完成", "晴", "{\"temperature\":26}");
        when(providerAdapter.execute(eq(executionContext), eq(tool), any(ToolInvocationRequest.class))).thenReturn(result);

        routerService.executeResolvedTool(executionContext, tool, request);

        verify(eventService).requested(executionContext, tool, request, null);
        verify(eventService).started(eventContext);
        verify(eventService).completed(any(), any(), anyLong());
    }

    @Test
    void shouldPublishFailedWhenProviderFails() {
        ToolExecutionException exception = new ToolExecutionException(
                "调用超时", "TOOL_TIMEOUT", "timeout", true, null, Map.of());
        when(providerAdapter.execute(eq(executionContext), eq(tool), any(ToolInvocationRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> routerService.executeResolvedTool(executionContext, tool, request))
                .isSameAs(exception);

        verify(eventService).started(eventContext);
        verify(eventService).failed(any(), any(), anyLong());
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
