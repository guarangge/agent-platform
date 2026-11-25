package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;

/**
 * AgentLoopKernel 执行请求。
 *
 * @param executionRequest 稳定运行时执行请求
 * @param runtimeContext 由 RuntimeContextProvider 解析的稳定运行上下文
 */
public record AgentLoopExecutionRequest(
        RuntimeExecutionRequest executionRequest,
        RuntimeContextProvider.ResolvedRuntimeContext runtimeContext
) {
    public AgentLoopExecutionRequest {
        if (executionRequest == null) {
            throw new IllegalArgumentException("AgentLoopKernel executionRequest 不能为空");
        }
        if (runtimeContext == null) {
            throw new IllegalArgumentException("AgentLoopKernel runtimeContext 不能为空");
        }
    }
}
