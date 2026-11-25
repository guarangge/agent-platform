package com.zc.agent.core.modules.runtime.kernel;

/** 可替换的 Agent 执行循环内核契约。 */
public interface AgentLoopKernel {

    /** 返回稳定内核编码，用于 Trace、灰度和影子对比。 */
    String kernelCode();

    /** 执行一次完整计划循环。 */
    AgentLoopExecutionResult execute(AgentLoopExecutionRequest request);
}
