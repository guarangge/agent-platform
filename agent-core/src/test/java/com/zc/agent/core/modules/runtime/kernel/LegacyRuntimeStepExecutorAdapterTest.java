package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.ApprovalStepService;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.runtime.service.ExecutionStepHandlerRegistry;
import com.zc.agent.core.modules.runtime.service.StepOutputContractService;
import com.zc.agent.core.modules.workflow.model.WorkflowExecutionResult;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import com.zc.agent.core.modules.workflow.service.WorkflowExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyRuntimeStepExecutorAdapterTest {

    private WorkflowExecutorService workflowExecutorService;
    private ApprovalStepService approvalStepService;
    private ExecutionStepHandlerRegistry executionStepHandlerRegistry;
    private StepOutputContractService stepOutputContractService;
    private LegacyRuntimeStepExecutorAdapter adapter;

    @BeforeEach
    void setUp() {
        workflowExecutorService = mock(WorkflowExecutorService.class);
        approvalStepService = mock(ApprovalStepService.class);
        executionStepHandlerRegistry = mock(ExecutionStepHandlerRegistry.class);
        stepOutputContractService = mock(StepOutputContractService.class);
        adapter = new LegacyRuntimeStepExecutorAdapter(
                workflowExecutorService,
                approvalStepService,
                executionStepHandlerRegistry,
                stepOutputContractService
        );
    }

    @Test
    void shouldKeepWorkflowResumeAndSnapshotContract() {
        RuntimeStepExecutionCommand stepRequest = request("workflow");
        WorkflowResumeContext resumeContext = mock(WorkflowResumeContext.class);
        WorkflowExecutionResult workflowResult = new WorkflowExecutionResult(
                "WF_1", "Workflow", "执行摘要", "执行结果", "{}");
        when(workflowExecutorService.execute(
                stepRequest.executionContext(),
                stepRequest.step(),
                stepRequest.authorization(),
                resumeContext,
                stepRequest.taskStepId(),
                stepRequest.operatorId()
        )).thenReturn(workflowResult);
        when(stepOutputContractService.buildWorkflowSnapshot(workflowResult)).thenReturn("{\"type\":\"workflow\"}");

        RuntimeStepExecutionOutcome result = adapter.execute(
                new RuntimeStepExecutionRequest(stepRequest, resumeContext));

        assertThat(adapter.executorCode()).isEqualTo(LegacyRuntimeStepExecutorAdapter.EXECUTOR_CODE);
        assertThat(result.responseText()).isEqualTo("执行结果");
        assertThat(result.summary()).isEqualTo("执行摘要");
        assertThat(result.outputSnapshot()).isEqualTo("{\"type\":\"workflow\"}");
        verifyNoInteractions(approvalStepService, executionStepHandlerRegistry);
    }

    @Test
    void shouldPropagateApprovalSuspensionWithoutFallingThroughToRegistry() {
        RuntimeStepExecutionCommand stepRequest = request("approval");
        ApprovalRequiredException approvalRequired = new ApprovalRequiredException(
                "当前计划等待审批",
                "STEP_1",
                "测试步骤",
                null,
                List.of(),
                List.of(),
                "plan_step",
                2
        );
        doThrow(approvalRequired).when(approvalStepService).execute(stepRequest.step());

        assertThatThrownBy(() -> adapter.execute(new RuntimeStepExecutionRequest(stepRequest, null)))
                .isSameAs(approvalRequired);

        verify(approvalStepService).execute(stepRequest.step());
        verifyNoInteractions(workflowExecutorService, executionStepHandlerRegistry);
    }

    @Test
    void shouldDelegateOtherStepTypesWithoutChangingRequestOrResult() {
        RuntimeStepExecutionCommand stepRequest = request("tool");
        RuntimeStepExecutionOutcome expected = new RuntimeStepExecutionOutcome(
                "Tool 结果", "{\"type\":\"tool\"}", "Tool 摘要");
        when(executionStepHandlerRegistry.execute(stepRequest)).thenReturn(expected);

        RuntimeStepExecutionOutcome result = adapter.execute(
                new RuntimeStepExecutionRequest(stepRequest, null));

        assertThat(result).isSameAs(expected);
        verify(executionStepHandlerRegistry).execute(stepRequest);
        verifyNoInteractions(workflowExecutorService, approvalStepService, stepOutputContractService);
    }

    private RuntimeStepExecutionCommand request(String stepType) {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1,
                "STEP_1",
                stepType,
                "测试步骤",
                "验证执行边界",
                List.of(),
                "tool".equals(stepType) ? "TOOL_1" : null,
                "workflow".equals(stepType) ? 10L : null,
                Map.of(),
                "标准输出",
                "fail_fast",
                false,
                "pending",
                "测试"
        );
        return new RuntimeStepExecutionCommand(
                executionContext,
                null,
                null,
                step,
                List.of(),
                "input",
                Map.of(),
                "Bearer token",
                20L,
                30L
        );
    }
}
