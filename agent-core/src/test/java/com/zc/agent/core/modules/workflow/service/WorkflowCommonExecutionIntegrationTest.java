package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.memory.service.MemoryInjectionService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.service.ConditionStepService;
import com.zc.agent.core.modules.runtime.service.ExecutionInputPolicyService;
import com.zc.agent.core.modules.runtime.service.ExecutionResponseAssemblerService;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.runtime.service.ExecutionStepHandler;
import com.zc.agent.core.modules.runtime.service.ExecutionStepHandlerRegistry;
import com.zc.agent.core.modules.runtime.service.GuardrailPolicyService;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowCommonExecutionIntegrationTest {

    @Test
    void shouldExecuteWorkflowNodeThroughSharedHandlerRegistry() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicBoolean invoked = new AtomicBoolean(false);
        ExecutionStepHandler handler = new ExecutionStepHandler() {
            @Override
            public Set<ExecutionStepType> supportedTypes() {
                return Set.of(ExecutionStepType.WAIT);
            }

            @Override
            public RuntimeStepExecutionOutcome execute(RuntimeStepExecutionCommand request) {
                invoked.set(true);
                return new RuntimeStepExecutionOutcome(
                        "等待完成",
                        "{\"resultType\":\"wait\",\"assistantMessage\":\"等待完成\",\"summary\":\"公共处理器执行成功\"}",
                        "公共处理器执行成功"
                );
            }
        };
        RegistryAgentClient registryClient = mock(RegistryAgentClient.class);
        when(registryClient.getWorkflowSnapshot(100L, "Bearer test")).thenReturn(new RegistryWorkflowSnapshot(
                100L, "WF_WAIT", "等待流程", "orchestration", "general", "published", definition(objectMapper), null
        ));
        TaskTraceService taskTraceService = mock(TaskTraceService.class);
        WorkflowRecoveryService recoveryService = mock(WorkflowRecoveryService.class);
        WorkflowExecutorService executor = new WorkflowExecutorService(
                registryClient,
                mock(ToolRouterService.class),
                mock(MemoryInjectionService.class),
                mock(ConditionStepService.class),
                new ExecutionStepHandlerRegistry(List.of(handler)),
                mock(ExecutionResponseAssemblerService.class),
                mock(ExecutionInputPolicyService.class),
                mock(GuardrailPolicyService.class),
                recoveryService,
                new WorkflowGraphNavigator(),
                new WorkflowResultAssemblerService(recoveryService, objectMapper),
                new WorkflowNodeObservabilityService(taskTraceService),
                objectMapper
        );

        var result = executor.execute(context(), planStep(), "Bearer test");

        assertThat(invoked).isTrue();
        assertThat(result.responseText())
                .contains("已执行 Workflow「等待流程」")
                .contains("公共处理器执行成功")
                .contains("等待完成");
        assertThat(result.summary()).isEqualTo("公共处理器执行成功");
        assertThat(result.outputSnapshot()).contains("wait-node");
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskTraceService, times(4)).recordSpan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                eventTypeCaptor.capture(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(eventTypeCaptor.getAllValues())
                .containsExactly(
                        "workflow_node_started", "workflow_node_completed",
                        "workflow_node_started", "workflow_node_completed"
                );
    }

    private JsonNode definition(ObjectMapper objectMapper) throws Exception {
        return objectMapper.readTree("""
                {
                  "nodes":[
                    {"id":"start","type":"start","name":"开始"},
                    {"id":"wait-node","type":"wait","name":"等待","durationMs":1},
                    {"id":"end","type":"end","name":"结束"}
                  ],
                  "edges":[
                    {"source":"start","target":"wait-node"},
                    {"source":"wait-node","target":"end"}
                  ]
                }
                """);
    }

    private ExecutionPlan.ExecutionPlanStep planStep() {
        return new ExecutionPlan.ExecutionPlanStep(
                1, "workflow-step", "workflow", "执行流程", "执行等待流程", List.of(), null, 100L,
                Map.of(), "完成", "fail_fast", false, "draft", "test"
        );
    }

    private ExecutionContext context() {
        return new ExecutionContext(
                new ExecutionContext.CurrentUserContext(1L, "tester", "Tester", List.of(), List.of()),
                null, null, null, null, null, List.of(),
                new ExecutionContext.ConversationContext(1L, "CONV_1", "测试", "processing", null),
                new ExecutionContext.AgentContext(1L, "GENERAL", "通用 Agent", null, "enabled", null),
                null, null, null,
                new ExecutionContext.TimeContext("2026-07-15", "2026-07-15T15:00:00", "Asia/Shanghai"),
                null,
                Map.of(),
                new ExecutionContext.MessageContext(1L, "执行"),
                new ExecutionContext.TaskContext(1L, "TASK_1", "TRACE_1"),
                List.of(), List.of(), List.of()
        );
    }
}
