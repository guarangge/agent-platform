package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;

import java.util.List;

/**
 * @param executionContext 当前完整运行上下文
 * @param iteration 当前迭代序号
 * @param observations 已产生的有界 Observation
 */
public record InteractiveLoopActionRequest(
        ExecutionContext executionContext,
        int iteration,
        List<InteractiveLoopObservation> observations
) {
}
