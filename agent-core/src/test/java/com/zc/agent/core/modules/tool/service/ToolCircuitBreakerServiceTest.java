package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.repository.ToolRuntimeGovernanceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCircuitBreakerServiceTest {

    private final ToolRuntimeGovernanceRepository repository = mock(ToolRuntimeGovernanceRepository.class);
    private final ToolCircuitBreakerService service = new ToolCircuitBreakerService(repository);

    @Test
    void shouldSkipOpenCircuitBeforeRecoveryTime() {
        RegistryAgentTool tool = tool();
        ExecutionContext context = context();
        when(repository.findState(9L)).thenReturn(Optional.of(
                new ToolRuntimeGovernanceRepository.RuntimeState(9L, "open", 3, LocalDateTime.now().plusMinutes(1))));

        assertThat(service.allow(context, tool)).isFalse();
        verify(repository).insertAudit(1L, 9L, "SEARCH", "search_prod", "circuit_skip",
                "rejected", "TRACE_1", "TOOL_CIRCUIT_OPEN", "实例熔断中，跳过路由", "{}");
    }

    @Test
    void shouldAllowHalfOpenProbeAfterRecoveryTime() {
        RegistryAgentTool tool = tool();
        when(repository.findState(9L)).thenReturn(Optional.of(
                new ToolRuntimeGovernanceRepository.RuntimeState(9L, "open", 3, LocalDateTime.now().minusSeconds(1))));

        assertThat(service.allow(context(), tool)).isTrue();
        verify(repository).markHalfOpen(9L);
    }

    private ExecutionContext context() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionContext.TaskContext taskContext = mock(ExecutionContext.TaskContext.class);
        when(context.taskContext()).thenReturn(taskContext);
        when(taskContext.traceId()).thenReturn("TRACE_1");
        return context;
    }

    private RegistryAgentTool tool() {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        ObjectNode route = metadata.putObject("lifecycleRoute");
        route.put("deploymentId", 9L);
        route.put("deploymentCode", "search_prod");
        route.put("failureThreshold", 3);
        route.put("recoverySeconds", 60);
        return new RegistryAgentTool(1L, "SEARCH", "搜索", "function", "general", null, null,
                "mcp", "streamable_http", "p", "p", "search_prod", "prod", "prod", "http://localhost",
                "none", null, null, 30, 0, "search", "tool", null, null, null,
                "default", 30, "enabled", "", metadata);
    }
}
