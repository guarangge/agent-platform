package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.runtime.protocol.service.ApprovalRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyApprovalCoordinatorAdapterTest {

    private ApprovalRuntimeEventService approvalRuntimeEventService;
    private LegacyApprovalCoordinatorAdapter adapter;

    @BeforeEach
    void setUp() {
        approvalRuntimeEventService = mock(ApprovalRuntimeEventService.class);
        adapter = new LegacyApprovalCoordinatorAdapter(approvalRuntimeEventService);
    }

    @Test
    void shouldDelegateApprovalRequestWithoutChangingRecoveryMetadata() {
        ApprovalCoordinator.ApprovalRequest request = new ApprovalCoordinator.ApprovalRequest(
                1L, 10L, 30L, "APPROVAL_1", "高风险 Tool 审批", "workflow",
                "{\"nextNodeId\":\"END\"}", "TRACE_1", 20L);
        ApprovalRuntimeEventService.RequestApprovalCommand legacyCommand =
                new ApprovalRuntimeEventService.RequestApprovalCommand(
                        1L, 10L, 30L, "APPROVAL_1", "高风险 Tool 审批", "workflow",
                        "{\"nextNodeId\":\"END\"}", "TRACE_1", 20L);
        when(approvalRuntimeEventService.request(legacyCommand)).thenReturn(40L);

        Long approvalId = adapter.request(request);

        assertThat(adapter.coordinatorCode()).isEqualTo(LegacyApprovalCoordinatorAdapter.COORDINATOR_CODE);
        assertThat(approvalId).isEqualTo(40L);
        verify(approvalRuntimeEventService).request(legacyCommand);
    }

    @Test
    void shouldDelegateApprovalTransitionAndPropagateAtomicFailure() {
        ApprovalRecord approval = new ApprovalRecord(
                40L, 1L, 10L, 30L, "APPROVAL_1", "高风险 Tool 审批", "pending",
                null, null, null, "{}", LocalDateTime.of(2026, 9, 1, 10, 0));
        ApprovalCoordinator.ApprovalTransition transition = new ApprovalCoordinator.ApprovalTransition(
                approval, "approved", "审批通过并继续执行", "workflow", "TRACE_1", 20L);
        ApprovalRuntimeEventService.TransitionApprovalCommand legacyCommand =
                new ApprovalRuntimeEventService.TransitionApprovalCommand(
                        approval, "approved", "审批通过并继续执行", "workflow", "TRACE_1", 20L);
        IllegalStateException failure = new IllegalStateException("event store unavailable");
        doThrow(failure).when(approvalRuntimeEventService).transition(legacyCommand);

        assertThatThrownBy(() -> adapter.transition(transition)).isSameAs(failure);
    }

    @Test
    void shouldCloseLegacyRunScopeAfterApprovalResume() {
        AgentRunStreamingContext.Scope legacyScope = mock(AgentRunStreamingContext.Scope.class);
        when(approvalRuntimeEventService.openRunScope(10L)).thenReturn(legacyScope);

        try (ApprovalCoordinator.Scope ignored = adapter.openRunScope(10L)) {
            verify(approvalRuntimeEventService).openRunScope(10L);
        }

        verify(legacyScope).close();
    }
}
