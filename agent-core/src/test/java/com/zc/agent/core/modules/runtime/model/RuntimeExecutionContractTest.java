package com.zc.agent.core.modules.runtime.model;

import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeExecutionContractTest {

    @Test
    void shouldPreserveExecutionAndRecoverySnapshotsWithoutConversion() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = mock(ExecutionDecision.class);
        ExecutionPlan plan = mock(ExecutionPlan.class);
        List<RuntimeStepResultContext> stepResults = List.of(new RuntimeStepResultContext(
                1, "tool_1", "tool", "查询", "完成", "结果", "{}"));
        RuntimeExecutionRequest.RecoveryReplaySnapshot recovery =
                new RuntimeExecutionRequest.RecoveryReplaySnapshot(
                        true, 20L, 1, "查询", List.of("已恢复查询结果"));
        RuntimeExecutionRequest.ApprovalReplaySnapshot approval =
                new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                        30L, "guardrail:tool_1", "高风险 Tool 审批", "批准", "approved", null, null);
        WorkflowResumeContext workflowResume = new WorkflowResumeContext(
                "NODE_2", "approval_resume", List.of(), List.of());

        RuntimeExecutionRequest request = new RuntimeExecutionRequest(
                context, decision, plan, 10L, "TRACE_1", "{}", "Bearer token", 1L,
                2, 3, stepResults, recovery, approval, workflowResume);

        assertThat(request.executionContext()).isSameAs(context);
        assertThat(request.executionDecision()).isSameAs(decision);
        assertThat(request.executionPlan()).isSameAs(plan);
        assertThat(request.recoveredStepResults()).isSameAs(stepResults);
        assertThat(request.recoveryReplaySnapshot()).isSameAs(recovery);
        assertThat(request.approvalReplaySnapshot()).isSameAs(approval);
        assertThat(request.workflowResumeContext()).isSameAs(workflowResume);
    }

    @Test
    void shouldKeepSuspensionStateSemantics() {
        ExecutionPlan plan = mock(ExecutionPlan.class);

        RuntimeExecutionAggregate waitingApproval = new RuntimeExecutionAggregate(
                "等待审批", true, plan);
        RuntimeExecutionAggregate waitingEvent = new RuntimeExecutionAggregate(
                "等待事件", false, plan, "waiting_event");
        RuntimeExecutionAggregate paused = new RuntimeExecutionAggregate(
                "人工暂停", false, plan, "paused");

        assertThat(waitingApproval.waitingApproval()).isTrue();
        assertThat(waitingApproval.suspensionType()).isEqualTo("waiting_approval");
        assertThat(waitingEvent.waitingEvent()).isTrue();
        assertThat(paused.paused()).isTrue();
    }
}
