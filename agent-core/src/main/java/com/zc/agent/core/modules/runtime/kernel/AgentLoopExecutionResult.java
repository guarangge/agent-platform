package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;

/**
 * AgentLoopKernel 执行结果。
 *
 * @param kernelCode 实际执行内核编码
 * @param aggregate 稳定执行聚合结果
 */
public record AgentLoopExecutionResult(
        String kernelCode,
        RuntimeExecutionAggregate aggregate
) {
    public AgentLoopExecutionResult {
        if (kernelCode == null || kernelCode.isBlank()) {
            throw new IllegalArgumentException("AgentLoopKernel kernelCode 不能为空");
        }
        if (aggregate == null) {
            throw new IllegalArgumentException("AgentLoopKernel aggregate 不能为空");
        }
    }
}
