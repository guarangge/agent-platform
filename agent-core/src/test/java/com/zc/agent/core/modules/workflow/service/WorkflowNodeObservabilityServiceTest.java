package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WorkflowNodeObservabilityServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecordStartedAndWaitingOnlyOnce() throws Exception {
        TaskTraceService traceService = mock(TaskTraceService.class);
        WorkflowNodeObservabilityService service = new WorkflowNodeObservabilityService(traceService);
        var scope = service.start(context(), workflow(), objectMapper.readTree("{\"type\":\"approval\",\"name\":\"审批\"}"),
                "approval-1", "approval", 9L, 1L, Map.of(), List.of());

        scope.waiting(Map.of("status", "waiting_approval"));
        scope.succeedIfNecessary(Map.of("status", "should_not_write"));

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceService, times(2)).recordSpan(
                any(), any(), any(), eventCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(eventCaptor.getAllValues()).containsExactly("workflow_node_started", "workflow_node_waiting");
    }

    @Test
    void shouldRecordStartedAndFailedOnlyOnce() throws Exception {
        TaskTraceService traceService = mock(TaskTraceService.class);
        WorkflowNodeObservabilityService service = new WorkflowNodeObservabilityService(traceService);
        var scope = service.start(context(), workflow(), objectMapper.readTree("{\"type\":\"tool\",\"name\":\"检索\"}"),
                "tool-1", "tool", 9L, 1L, Map.of(), List.of());

        scope.failed(new IllegalStateException("tool failed"));
        scope.succeedIfNecessary(Map.of());

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceService, times(2)).recordSpan(
                any(), any(), any(), eventCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(eventCaptor.getAllValues()).containsExactly("workflow_node_started", "workflow_node_failed");
    }

    private RegistryWorkflowSnapshot workflow() throws Exception {
        return new RegistryWorkflowSnapshot(10L, "WF_TEST", "测试流程", "orchestration", "general", "published",
                objectMapper.readTree("{\"nodes\":[],\"edges\":[]}"), null);
    }

    private ExecutionContext context() {
        return new ExecutionContext(
                new ExecutionContext.CurrentUserContext(1L, "tester", "Tester", List.of(), List.of()),
                null, null, null, null, null, List.of(),
                new ExecutionContext.ConversationContext(1L, "CONV_1", "测试", "processing", null),
                new ExecutionContext.AgentContext(1L, "GENERAL", "通用 Agent", null, "enabled", null),
                null, null, null,
                new ExecutionContext.TimeContext("2026-07-16", "2026-07-16T10:00:00", "Asia/Shanghai"),
                null,
                Map.of(),
                new ExecutionContext.MessageContext(1L, "执行"),
                new ExecutionContext.TaskContext(1L, "TASK_1", "TRACE_1"),
                List.of(), List.of(), List.of()
        );
    }
}
