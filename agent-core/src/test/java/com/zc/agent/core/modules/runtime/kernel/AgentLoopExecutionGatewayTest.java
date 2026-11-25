package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionMode;
import com.zc.agent.core.modules.runtime.service.ExecutionOrchestratorService;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeExecutionModeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentLoopExecutionGatewayTest {

    private ExecutionOrchestratorService legacyOrchestrator;
    private AgentLoopKernel workflowPlanKernel;
    private AgentLoopKernel interactiveLoopKernel;
    private ExecutionModeStrategy strategy;
    private RuntimeContextProvider runtimeContextProvider;
    private RuntimeExecutionModePolicy runtimeExecutionModePolicy;
    private RuntimeExecutionModeEventService runtimeExecutionModeEventService;
    private AgentLoopExecutionGateway gateway;
    private RuntimeExecutionRequest request;
    private RuntimeExecutionAggregate aggregate;

    @BeforeEach
    void setUp() {
        legacyOrchestrator = mock(ExecutionOrchestratorService.class);
        workflowPlanKernel = mock(AgentLoopKernel.class);
        interactiveLoopKernel = mock(AgentLoopKernel.class);
        strategy = mock(ExecutionModeStrategy.class);
        runtimeContextProvider = mock(RuntimeContextProvider.class);
        runtimeExecutionModePolicy = mock(RuntimeExecutionModePolicy.class);
        runtimeExecutionModeEventService = mock(RuntimeExecutionModeEventService.class);
        gateway = new AgentLoopExecutionGateway(
                legacyOrchestrator, workflowPlanKernel, interactiveLoopKernel, strategy, runtimeContextProvider,
                runtimeExecutionModePolicy, runtimeExecutionModeEventService);
        request = new RuntimeExecutionRequest(
                null, null, null, 20L, "TRACE_1", "{}", "Bearer token", 1L,
                1, 0, List.of(), null, null, null);
        when(runtimeContextProvider.resolve(null)).thenReturn(new RuntimeContextProvider.ResolvedRuntimeContext(
                null, 1L, 1L, 1L, 12L, 1L, 100L, 10L));
        when(runtimeExecutionModePolicy.select(org.mockito.ArgumentMatchers.any())).thenReturn(
                new RuntimeExecutionModePolicy.Selection(
                        RuntimeExecutionMode.WORKFLOW_PLAN, RuntimeExecutionMode.WORKFLOW_PLAN,
                        List.of(RuntimeExecutionMode.WORKFLOW_PLAN), "agent_default", false, null));
        aggregate = new RuntimeExecutionAggregate(
                "回答", false, mock(ExecutionPlan.class));
    }

    @Test
    void shouldUseLegacyDirectPathWhenKernelFlagIsOff() {
        when(strategy.select(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExecutionModeStrategy.Selection(false, "kernel_disabled", null));
        when(legacyOrchestrator.executePlan(request)).thenReturn(aggregate);

        var result = gateway.execute(request);

        assertThat(result).isSameAs(aggregate);
        verify(legacyOrchestrator).executePlan(request);
        verify(runtimeContextProvider).resolve(null);
        verify(runtimeExecutionModeEventService).selected(
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq("TRACE_1"),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(workflowPlanKernel, interactiveLoopKernel);
    }

    @Test
    void shouldUseKernelAdapterWithoutChangingAggregateContract() {
        when(strategy.select(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExecutionModeStrategy.Selection(true, "agent_allowlisted", null));
        when(workflowPlanKernel.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentLoopExecutionResult("legacy_orchestrator_adapter_v1", aggregate));

        var result = gateway.execute(request);

        assertThat(result).isSameAs(aggregate);
        verifyNoInteractions(legacyOrchestrator);
        verify(runtimeContextProvider).resolve(null);
        verify(runtimeExecutionModeEventService).selected(
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq("TRACE_1"),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(interactiveLoopKernel);
        verify(workflowPlanKernel).execute(org.mockito.ArgumentMatchers.argThat(
                item -> item.executionRequest() == request
                        && Long.valueOf(12L).equals(item.runtimeContext().projectId())));
    }

    @Test
    void shouldUseInteractiveKernelIndependentlyFromLegacyKernelFlag() {
        when(runtimeExecutionModePolicy.select(org.mockito.ArgumentMatchers.any())).thenReturn(
                new RuntimeExecutionModePolicy.Selection(
                        RuntimeExecutionMode.INTERACTIVE_LOOP, RuntimeExecutionMode.INTERACTIVE_LOOP,
                        List.of(RuntimeExecutionMode.WORKFLOW_PLAN, RuntimeExecutionMode.INTERACTIVE_LOOP),
                        "interactive_loop_agent_allowlisted", false, null));
        when(strategy.select(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExecutionModeStrategy.Selection(false, "kernel_disabled", null));
        when(interactiveLoopKernel.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentLoopExecutionResult("interactive_loop_kernel_v1", aggregate));

        RuntimeExecutionAggregate result = gateway.execute(request);

        assertThat(result).isSameAs(aggregate);
        verify(interactiveLoopKernel).execute(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(legacyOrchestrator, workflowPlanKernel);
    }
}
