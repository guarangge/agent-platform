package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.protocol.service.InteractiveLoopRuntimeEventService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractiveLoopControllerTest {

    private InteractiveLoopBudgetPolicyResolver policyResolver;
    private InteractiveLoopActionDecider actionDecider;
    private InteractiveLoopParameterHasher parameterHasher;
    private ToolRouterService toolRouterService;
    private InteractiveLoopRuntimeEventService eventService;
    private InteractiveLoopCheckpointStore checkpointStore;
    private InteractiveLoopCancellationSignal cancellationSignal;
    private InteractiveLoopController controller;
    private ExecutionContext context;
    private RegistryAgentTool tool;

    @BeforeEach
    void setUp() {
        policyResolver = mock(InteractiveLoopBudgetPolicyResolver.class);
        actionDecider = mock(InteractiveLoopActionDecider.class);
        parameterHasher = mock(InteractiveLoopParameterHasher.class);
        toolRouterService = mock(ToolRouterService.class);
        eventService = mock(InteractiveLoopRuntimeEventService.class);
        checkpointStore = mock(InteractiveLoopCheckpointStore.class);
        cancellationSignal = mock(InteractiveLoopCancellationSignal.class);
        controller = new InteractiveLoopController(
                policyResolver, actionDecider, parameterHasher, toolRouterService, eventService, checkpointStore,
                cancellationSignal);
        context = mock(ExecutionContext.class);
        tool = mock(RegistryAgentTool.class);
        when(tool.toolCode()).thenReturn("SEARCH");
        when(context.availableTools()).thenReturn(List.of(tool));
        when(context.userMessage()).thenReturn("查询 Agent 资料");
        when(policyResolver.resolve(context)).thenReturn(policy(5, 5, 5, 1000, 2));
        when(actionDecider.estimate(any())).thenReturn(
                new InteractiveLoopActionDecider.ProjectedUsage(100, BigDecimal.ONE));
        when(parameterHasher.hash(anyMap())).thenReturn("parameter-hash");
    }

    @Test
    void shouldExecuteAllowedToolThenReturnFinalAnswer() throws Exception {
        when(actionDecider.decide(any()))
                .thenReturn(toolAction(), finalAction("检索完成"));
        when(toolRouterService.execute(eq(context), eq("SEARCH"), eq("查询 Agent 资料"), anyMap()))
                .thenReturn(Optional.of(new ToolExecutionResult(
                        "SEARCH", "搜索", "命中 1 条", "Agent 平台资料", "{}")));

        InteractiveLoopResult result = controller.execute(context, 10L, "TRACE_1");

        assertThat(result.stopReason()).isEqualTo(InteractiveLoopStopReason.COMPLETED);
        assertThat(result.assistantContent()).isEqualTo("检索完成");
        assertThat(result.budgetSnapshot().iterations()).isEqualTo(2);
        assertThat(result.budgetSnapshot().llmCalls()).isEqualTo(2);
        assertThat(result.budgetSnapshot().toolCalls()).isEqualTo(1);
        assertThat(result.observations()).singleElement().satisfies(observation -> {
            assertThat(observation.status()).isEqualTo("success");
            assertThat(observation.responseText()).isEqualTo("Agent 平台资料");
        });
        verify(eventService).stopped(eq(10L), eq("TRACE_1"),
                org.mockito.ArgumentMatchers.argThat(snapshot ->
                        snapshot.stopReason() == InteractiveLoopStopReason.COMPLETED));
    }

    @Test
    void shouldStopRepeatedToolBeforeThirdProviderCall() throws Exception {
        when(actionDecider.decide(any())).thenReturn(toolAction(), toolAction(), toolAction());
        when(toolRouterService.execute(eq(context), eq("SEARCH"), any(), anyMap()))
                .thenReturn(Optional.of(new ToolExecutionResult("SEARCH", "搜索", "same", "same", "{}")));

        InteractiveLoopResult result = controller.execute(context, 10L, "TRACE_1");

        assertThat(result.stopReason()).isEqualTo(InteractiveLoopStopReason.DUPLICATE_TOOL_CALL);
        assertThat(result.budgetSnapshot().toolCalls()).isEqualTo(2);
        verify(toolRouterService, org.mockito.Mockito.times(2))
                .execute(eq(context), eq("SEARCH"), any(), anyMap());
    }

    @Test
    void shouldRejectToolOutsideEffectiveAgentScope() throws Exception {
        when(actionDecider.decide(any())).thenReturn(new InteractiveLoopAction(
                InteractiveLoopAction.Type.TOOL_CALL, "DELETE_ALL", Map.of(), null));

        InteractiveLoopResult result = controller.execute(context, 10L, "TRACE_1");

        assertThat(result.stopReason()).isEqualTo(InteractiveLoopStopReason.CAPABILITY_POLICY);
        assertThat(result.budgetSnapshot().toolCalls()).isZero();
        verify(toolRouterService, never()).execute(any(), any(), any(), anyMap());
    }

    @Test
    void shouldPublishStopAndPropagateApproval() throws Exception {
        when(actionDecider.decide(any())).thenReturn(toolAction());
        when(toolRouterService.execute(eq(context), eq("SEARCH"), any(), anyMap()))
                .thenThrow(new ApprovalRequiredException(
                        "需要审批", "capability:SEARCH:1", "能力审批", null, List.of(), List.of(),
                        "capability_policy", null,
                        Map.of("toolCallId", "TOOL_CALL_0123456789ABCDEF0123",
                                "parameterHash", "a".repeat(64))));

        assertThatThrownBy(() -> controller.execute(context, 10L, "TRACE_1"))
                .isInstanceOf(ApprovalRequiredException.class);
        verify(eventService).stopped(eq(10L), eq("TRACE_1"),
                org.mockito.ArgumentMatchers.argThat(snapshot ->
                        snapshot.stopReason() == InteractiveLoopStopReason.APPROVAL_REQUIRED));
        verify(checkpointStore).saveWaitingApproval(eq(10L), eq("TRACE_1"), any(),
                eq(List.of()), eq(toolAction()), any(ApprovalRequiredException.class));
    }

    @Test
    void shouldReplayOnlyPendingToolThenContinueAfterApproval() throws Exception {
        InteractiveLoopAction pendingAction = toolAction();
        InteractiveLoopBudgetState savedBudget = new InteractiveLoopBudgetState(policy(5, 5, 5, 1000, 2));
        savedBudget.consumeIteration();
        savedBudget.consumeLlmCall(100, BigDecimal.ONE);
        savedBudget.consumeToolCall("SEARCH", "parameter-hash");
        InteractiveLoopObservation previous = new InteractiveLoopObservation(
                0, "PREVIOUS", "success", "已完成", "旧结果");
        InteractiveLoopCheckpoint checkpoint = new InteractiveLoopCheckpoint(
                99L, 10L, "TRACE_1", savedBudget.persistentSnapshot(), List.of(previous), pendingAction,
                "capability:SEARCH:1", "能力审批", "TOOL_CALL_0123456789ABCDEF0123",
                "a".repeat(64), Map.of());
        RuntimeExecutionRequest.ApprovalReplaySnapshot approval =
                new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                        1L, checkpoint.approvalNodeId(), checkpoint.approvalNodeName(), "通过",
                        "approved", checkpoint.toolCallId(), checkpoint.parameterHash());
        when(checkpointStore.latestWaitingApproval(10L)).thenReturn(Optional.of(checkpoint));
        when(toolRouterService.execute(eq(context), eq("SEARCH"), any(), anyMap()))
                .thenReturn(Optional.of(new ToolExecutionResult(
                        "SEARCH", "搜索", "审批后完成", "新结果", "{}")));
        when(actionDecider.decide(any())).thenReturn(finalAction("恢复完成"));

        InteractiveLoopResult result = controller.resumeAfterApproval(context, 10L, "TRACE_1", approval);

        assertThat(result.stopReason()).isEqualTo(InteractiveLoopStopReason.COMPLETED);
        assertThat(result.assistantContent()).isEqualTo("恢复完成");
        assertThat(result.observations()).hasSize(2);
        assertThat(result.budgetSnapshot().toolCalls()).isEqualTo(1);
        assertThat(result.budgetSnapshot().iterations()).isEqualTo(2);
        verify(toolRouterService, org.mockito.Mockito.times(1))
                .execute(eq(context), eq("SEARCH"), any(), anyMap());
        verify(checkpointStore).markRestored(99L);
    }

    @Test
    void shouldRejectMismatchedApprovalWithoutCallingTool() {
        InteractiveLoopBudgetState savedBudget = new InteractiveLoopBudgetState(policy(5, 5, 5, 1000, 2));
        InteractiveLoopCheckpoint checkpoint = new InteractiveLoopCheckpoint(
                99L, 10L, "TRACE_1", savedBudget.persistentSnapshot(), List.of(), toolAction(),
                "capability:SEARCH:1", "能力审批", "TOOL_CALL_0123456789ABCDEF0123",
                "a".repeat(64), Map.of());
        when(checkpointStore.latestWaitingApproval(10L)).thenReturn(Optional.of(checkpoint));
        RuntimeExecutionRequest.ApprovalReplaySnapshot approval =
                new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                        1L, checkpoint.approvalNodeId(), checkpoint.approvalNodeName(), "通过",
                        "approved", checkpoint.toolCallId(), "b".repeat(64));

        assertThatThrownBy(() -> controller.resumeAfterApproval(context, 10L, "TRACE_1", approval))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绑定不一致");
        verify(toolRouterService, never()).execute(any(), any(), any(), anyMap());
    }

    @Test
    void shouldStopBeforeLlmCallWhenAsyncRunIsCancelled() throws Exception {
        when(cancellationSignal.isCancellationRequested()).thenReturn(true);

        assertThatThrownBy(() -> controller.execute(context, 10L, "TRACE_1"))
                .isInstanceOf(CancellationException.class)
                .hasMessageContaining("已取消");

        verify(actionDecider, never()).decide(any());
        verify(toolRouterService, never()).execute(any(), any(), any(), anyMap());
        verify(eventService).stopped(eq(10L), eq("TRACE_1"),
                org.mockito.ArgumentMatchers.argThat(snapshot ->
                        snapshot.stopReason() == InteractiveLoopStopReason.CANCELLED));
    }

    private InteractiveLoopAction toolAction() {
        return new InteractiveLoopAction(
                InteractiveLoopAction.Type.TOOL_CALL, "SEARCH", Map.of("keyword", "agent"), null);
    }

    private InteractiveLoopAction finalAction(String content) {
        return new InteractiveLoopAction(
                InteractiveLoopAction.Type.FINAL_ANSWER, null, Map.of(), content);
    }

    private InteractiveLoopBudgetPolicy policy(int iterations,
                                               int llmCalls,
                                               int toolCalls,
                                               long tokens,
                                               int duplicates) {
        return new InteractiveLoopBudgetPolicy(
                iterations, llmCalls, toolCalls, tokens, BigDecimal.TEN, duplicates);
    }
}
