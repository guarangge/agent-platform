package com.zc.agent.core.modules.runtime.kernel;

/** 决定本次执行走旧直接调用还是 AgentLoopKernel Adapter。 */
public interface ExecutionModeStrategy {

    /** 对同一 Agent 返回稳定路由决策。 */
    Selection select(AgentLoopExecutionRequest request);

    /**
     * @param kernelEnabled 是否进入 AgentLoopKernel
     * @param reason 稳定决策原因
     * @param rolloutBucket 稳定百分比分桶；白名单和关闭场景为空
     */
    record Selection(
            boolean kernelEnabled,
            String reason,
            Integer rolloutBucket
    ) {
    }
}
