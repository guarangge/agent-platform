package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.memory.service.MemoryInjectionService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.*;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkflowFailureSemanticsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldFailWithRecoveryMetadataWhenStartHasNoOutgoingEdge() throws Exception {
        WorkflowExecutorService executor = executor(definition(""), mock(ConditionStepService.class));

        assertThatThrownBy(() -> executor.execute(context(), planStep(), "Bearer test"))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, exception -> {
                    assertThat(exception.getMessage()).contains("start 节点未配置可执行出口");
                    assertThat(exception.failedNodeId()).isEqualTo("start");
                    assertThat(exception.failedNodeType()).isEqualTo("start");
                    assertThat(exception.executedNodes()).isEmpty();
                });
    }

    @Test
    void shouldFailWhenTargetNodeDoesNotExist() throws Exception {
        WorkflowExecutorService executor = executor(definition("{\"source\":\"start\",\"target\":\"missing-node\"}"), mock(ConditionStepService.class));

        assertThatThrownBy(() -> executor.execute(context(), planStep(), "Bearer test"))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, exception -> {
                    assertThat(exception.getMessage()).contains("目标节点不存在");
                    assertThat(exception.failedNodeId()).isEqualTo("missing-node");
                    assertThat(exception.failedNodeType()).isEqualTo("missing");
                });
    }

    @Test
    void shouldFailWhenConditionHasNoMatchingBranch() throws Exception {
        ConditionStepService condition = mock(ConditionStepService.class);
        when(condition.evaluateWorkflowCondition(any(), any(), anyString())).thenReturn(true);
        JsonNode workflow = mapper.readTree("""
                {"nodes":[{"id":"start","type":"start"},{"id":"condition-1","type":"condition"}],
                 "edges":[{"source":"start","target":"condition-1"}]}
                """);
        WorkflowExecutorService executor = executor(workflow, condition);

        assertThatThrownBy(() -> executor.execute(context(), planStep(), "Bearer test"))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, exception -> {
                    assertThat(exception.getMessage()).contains("未配置匹配分支");
                    assertThat(exception.failedNodeId()).isEqualTo("condition-1");
                    assertThat(exception.executedNodes()).hasSize(1);
                    assertThat(exception.previousNodeResults()).hasSize(1);
                });
    }

    private WorkflowExecutorService executor(JsonNode definition, ConditionStepService condition) {
        RegistryAgentClient registry = mock(RegistryAgentClient.class);
        MemoryInjectionService memory = mock(MemoryInjectionService.class);
        when(registry.getWorkflowSnapshot(100L, "Bearer test")).thenReturn(
                new RegistryWorkflowSnapshot(100L, "WF_INVALID", "非法流程", "orchestration", "general", "published", definition, null));
        when(memory.resolveWorkflowInput(any(), eq(100L), anyList())).thenReturn(
                new MemoryInjectionService.ResolvedMemoryInput("none", List.of(), "执行"));
        WorkflowRecoveryService recoveryService = mock(WorkflowRecoveryService.class);
        return new WorkflowExecutorService(registry, mock(ToolRouterService.class), memory,
                condition, new ExecutionStepHandlerRegistry(List.of()), mock(ExecutionResponseAssemblerService.class),
                mock(ExecutionInputPolicyService.class), mock(GuardrailPolicyService.class), recoveryService,
                new WorkflowGraphNavigator(),
                new WorkflowResultAssemblerService(recoveryService, mapper),
                new WorkflowNodeObservabilityService(mock(TaskTraceService.class)), mapper);
    }

    private JsonNode definition(String edge) throws Exception {
        String edges = edge.isBlank() ? "" : edge;
        return mapper.readTree("{\"nodes\":[{\"id\":\"start\",\"type\":\"start\"}],\"edges\":[" + edges + "]}");
    }

    private ExecutionPlan.ExecutionPlanStep planStep() {
        return new ExecutionPlan.ExecutionPlanStep(1, "wf", "workflow", "执行", "执行", List.of(), null, 100L,
                Map.of(), "完成", "fail_fast", false, "draft", "test");
    }

    private ExecutionContext context() {
        return new ExecutionContext(new ExecutionContext.CurrentUserContext(1L, "tester", "Tester", List.of(), List.of()),
                null, null, null, null, null, List.of(), new ExecutionContext.ConversationContext(1L, "C1", "测试", "processing", null),
                new ExecutionContext.AgentContext(1L, "A1", "Agent", null, "enabled", null), null, null, null,
                new ExecutionContext.TimeContext("2026-07-16", "2026-07-16T10:00:00", "Asia/Shanghai"), null,
                Map.of(),
                new ExecutionContext.MessageContext(1L, "执行"), new ExecutionContext.TaskContext(1L, "T1", "TRACE1"),
                List.of(), List.of(), List.of());
    }
}
