package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.ExecutionOrchestratorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyAgentLoopKernelAdapterTest {

    @Test
    void shouldDelegateExactRequestToExistingOrchestrator() {
        ExecutionOrchestratorService orchestrator = mock(ExecutionOrchestratorService.class);
        LegacyAgentLoopKernelAdapter adapter = new LegacyAgentLoopKernelAdapter(orchestrator);
        var request = new RuntimeExecutionRequest(
                null, null, null, 20L, "TRACE_1", "{}", "Bearer token", 1L,
                1, 0, List.of(), null, null, null);
        var aggregate = new RuntimeExecutionAggregate(
                "回答", false, mock(ExecutionPlan.class));
        when(orchestrator.executePlan(request)).thenReturn(aggregate);

        AgentLoopExecutionResult result = adapter.execute(new AgentLoopExecutionRequest(
                request,
                new RuntimeContextProvider.ResolvedRuntimeContext(
                        null, 1L, 1L, 1L, 1L, 1L, 1L, 1L)
        ));

        assertThat(result.kernelCode()).isEqualTo(LegacyAgentLoopKernelAdapter.KERNEL_CODE);
        assertThat(result.aggregate()).isSameAs(aggregate);
        verify(orchestrator).executePlan(request);
    }
}
