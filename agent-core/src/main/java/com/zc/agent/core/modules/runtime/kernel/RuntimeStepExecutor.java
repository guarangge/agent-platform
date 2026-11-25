package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;

/** 可替换的单步运行时执行契约。 */
public interface RuntimeStepExecutor {

    /** 返回稳定执行器编码，用于 Trace 和后续插件路由。 */
    String executorCode();

    /** 执行一个标准化 Plan 步骤。 */
    RuntimeStepExecutionOutcome execute(RuntimeStepExecutionRequest request);
}
