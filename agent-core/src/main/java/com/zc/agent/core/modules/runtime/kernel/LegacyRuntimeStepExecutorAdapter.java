package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.service.ApprovalStepService;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.runtime.service.ExecutionStepHandlerRegistry;
import com.zc.agent.core.modules.runtime.service.StepOutputContractService;
import com.zc.agent.core.modules.workflow.service.WorkflowExecutorService;
import org.springframework.stereotype.Service;

/** 将现有 Workflow、Approval 和 Step Handler 路由适配为 RuntimeStepExecutor。 */
@Service
public class LegacyRuntimeStepExecutorAdapter implements RuntimeStepExecutor {

    public static final String EXECUTOR_CODE = "legacy_step_executor_v1";

    private final WorkflowExecutorService workflowExecutorService;
    private final ApprovalStepService approvalStepService;
    private final ExecutionStepHandlerRegistry executionStepHandlerRegistry;
    private final StepOutputContractService stepOutputContractService;

    public LegacyRuntimeStepExecutorAdapter(WorkflowExecutorService workflowExecutorService,
                                            ApprovalStepService approvalStepService,
                                            ExecutionStepHandlerRegistry executionStepHandlerRegistry,
                                            StepOutputContractService stepOutputContractService) {
        this.workflowExecutorService = workflowExecutorService;
        this.approvalStepService = approvalStepService;
        this.executionStepHandlerRegistry = executionStepHandlerRegistry;
        this.stepOutputContractService = stepOutputContractService;
    }

    @Override
    public String executorCode() {
        return EXECUTOR_CODE;
    }

    @Override
    public RuntimeStepExecutionOutcome execute(RuntimeStepExecutionRequest request) {
        RuntimeStepExecutionCommand command = request.command();
        if ("workflow".equals(command.step().stepType())) {
            var workflowResult = workflowExecutorService.execute(
                    command.executionContext(),
                    command.step(),
                    command.authorization(),
                    request.workflowResumeContext(),
                    command.taskStepId(),
                    command.operatorId()
            );
            return new RuntimeStepExecutionOutcome(
                    workflowResult.responseText(),
                    stepOutputContractService.buildWorkflowSnapshot(workflowResult),
                    workflowResult.summary()
            );
        }
        if ("approval".equals(command.step().stepType())) {
            approvalStepService.execute(command.step());
        }
        return executionStepHandlerRegistry.execute(command);
    }
}
