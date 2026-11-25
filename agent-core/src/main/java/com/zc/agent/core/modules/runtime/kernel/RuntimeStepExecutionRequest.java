package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;

/**
 * RuntimeStepExecutor 执行请求。
 *
 * @param command 单步稳定运行时执行命令
 * @param workflowResumeContext Workflow 恢复上下文，非 Workflow 步骤为空
 */
public record RuntimeStepExecutionRequest(
        RuntimeStepExecutionCommand command,
        WorkflowResumeContext workflowResumeContext
) {
    public RuntimeStepExecutionRequest {
        if (command == null) {
            throw new IllegalArgumentException("RuntimeStepExecutor command 不能为空");
        }
        if (command.step() == null) {
            throw new IllegalArgumentException("RuntimeStepExecutor step 不能为空");
        }
    }
}
