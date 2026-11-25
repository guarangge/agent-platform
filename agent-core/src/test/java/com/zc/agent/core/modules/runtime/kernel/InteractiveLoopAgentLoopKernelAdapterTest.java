package com.zc.agent.core.modules.runtime.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopBudgetPolicy;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopBudgetState;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopController;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopResult;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopStopReason;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractiveLoopAgentLoopKernelAdapterTest {

    private InteractiveLoopController controller;
    private RuntimeEventSink eventSink;
    private ApprovalCoordinator approvalCoordinator;
    private CheckpointStore checkpointStore;
    private InteractiveLoopAgentLoopKernelAdapter adapter;
    private RuntimeExecutionRequest request;

    @BeforeEach
    void setUp() {
        controller = mock(InteractiveLoopController.class);
        eventSink = mock(RuntimeEventSink.class);
        approvalCoordinator = mock(ApprovalCoordinator.class);
        checkpointStore = mock(CheckpointStore.class);
        adapter = new InteractiveLoopAgentLoopKernelAdapter(
                controller, eventSink, approvalCoordinator, checkpointStore, new ObjectMapper());
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.conversationContext()).thenReturn(
                new ExecutionContext.ConversationContext(8L, "C8", "会话", "processing", null));
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1, "S1", "tool", "动态执行", "完成目标", List.of(), "SEARCH", null,
                Map.of(), "结果", "fail_fast", false, "pending", "");
        ExecutionPlan plan = new ExecutionPlan(
                "P1", "standard", "目标", "ready", List.of(), List.of(), List.of(), List.of(step));
        request = new RuntimeExecutionRequest(
                context, null, plan, 10L, "TRACE_1", "{}", null, 1L,
                1, 0, List.of(), null, null, null);
        when(eventSink.stepStarted(any())).thenReturn(77L);
    }

    @Test
    void shouldMapCompletedLoopToStableAggregateAndStep() {
        InteractiveLoopBudgetState budget = new InteractiveLoopBudgetState(new InteractiveLoopBudgetPolicy(
                5, 5, 5, 1000, BigDecimal.TEN, 2));
        budget.stop(InteractiveLoopStopReason.COMPLETED);
        when(controller.execute(request.executionContext(), 10L, "TRACE_1"))
                .thenReturn(new InteractiveLoopResult(
                        "动态回答", InteractiveLoopStopReason.COMPLETED, budget.snapshot(), List.of()));

        AgentLoopExecutionResult result = adapter.execute(kernelRequest());

        assertThat(result.kernelCode()).isEqualTo(InteractiveLoopAgentLoopKernelAdapter.KERNEL_CODE);
        assertThat(result.aggregate().assistantContent()).isEqualTo("动态回答");
        assertThat(result.aggregate().waitingApproval()).isFalse();
        verify(eventSink).stepTransitioned(org.mockito.ArgumentMatchers.argThat(
                event -> "success".equals(event.status()) && Long.valueOf(77L).equals(event.taskStepId())));
        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(
                checkpoint -> "step_succeeded".equals(checkpoint.status())));
    }

    @Test
    void shouldMapLoopApprovalToExistingApprovalAndCheckpointChain() {
        ApprovalRequiredException approval = new ApprovalRequiredException(
                "需要审批", "capability:SEARCH:1", "能力审批", null, List.of(), List.of(),
                "capability_policy", null,
                Map.of("toolCallId", "TOOL_CALL_0123456789ABCDEF0123", "parameterHash", "a".repeat(64)));
        when(controller.execute(request.executionContext(), 10L, "TRACE_1")).thenThrow(approval);

        AgentLoopExecutionResult result = adapter.execute(kernelRequest());

        assertThat(result.aggregate().waitingApproval()).isTrue();
        assertThat(result.aggregate().suspensionType()).isEqualTo("waiting_approval");
        verify(eventSink).stepTransitioned(org.mockito.ArgumentMatchers.argThat(
                event -> "waiting_approval".equals(event.status())
                        && event.outputSnapshot().contains("capability_policy")));
        verify(approvalCoordinator).request(org.mockito.ArgumentMatchers.argThat(
                command -> Long.valueOf(8L).equals(command.conversationId())
                        && Long.valueOf(77L).equals(command.taskStepId())
                        && "capability_policy".equals(command.approvalSourceType())));
        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(
                checkpoint -> "waiting_approval".equals(checkpoint.status())));
    }

    @Test
    void shouldResumeFromLoopCheckpointWhenApprovalReplayExists() {
        RuntimeExecutionRequest.ApprovalReplaySnapshot approval =
                new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                        1L, "capability:SEARCH:1", "能力审批", "通过", "approved",
                        "TOOL_CALL_0123456789ABCDEF0123", "a".repeat(64));
        request = new RuntimeExecutionRequest(
                request.executionContext(), request.executionDecision(), request.executionPlan(),
                request.taskId(), request.traceId(), request.inputSnapshot(), request.authorization(),
                request.operatorId(), request.startStepNo(), request.stepNoOffset(),
                request.recoveredStepResults(), request.recoveryReplaySnapshot(), approval, null);
        InteractiveLoopBudgetState budget = new InteractiveLoopBudgetState(new InteractiveLoopBudgetPolicy(
                5, 5, 5, 1000, BigDecimal.TEN, 2));
        budget.stop(InteractiveLoopStopReason.COMPLETED);
        when(controller.resumeAfterApproval(
                request.executionContext(), 10L, "TRACE_1", approval))
                .thenReturn(new InteractiveLoopResult(
                        "恢复完成", InteractiveLoopStopReason.COMPLETED, budget.snapshot(), List.of()));

        AgentLoopExecutionResult result = adapter.execute(kernelRequest());

        assertThat(result.aggregate().assistantContent()).isEqualTo("恢复完成");
        verify(controller).resumeAfterApproval(request.executionContext(), 10L, "TRACE_1", approval);
        verify(controller, never()).execute(any(), any(), any());
    }

    @Test
    void shouldKeepFailedLoopOutOfTaskSuccessPath() {
        InteractiveLoopBudgetState budget = new InteractiveLoopBudgetState(new InteractiveLoopBudgetPolicy(
                5, 5, 5, 1000, BigDecimal.TEN, 2));
        budget.stop(InteractiveLoopStopReason.FAILED);
        when(controller.execute(request.executionContext(), 10L, "TRACE_1"))
                .thenReturn(new InteractiveLoopResult(
                        "动态循环执行失败", InteractiveLoopStopReason.FAILED, budget.snapshot(), List.of()));

        assertThatThrownBy(() -> adapter.execute(kernelRequest()))
                .hasMessageContaining("动态循环执行失败");
        verify(eventSink).stepTransitioned(org.mockito.ArgumentMatchers.argThat(
                event -> "failed".equals(event.status())));
        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(
                checkpoint -> "step_failed".equals(checkpoint.status())));
    }

    private AgentLoopExecutionRequest kernelRequest() {
        return new AgentLoopExecutionRequest(request,
                new RuntimeContextProvider.ResolvedRuntimeContext(
                        request.executionContext(), 1L, 1L, 1L, 1L, 1L, 8L, 1L));
    }
}
