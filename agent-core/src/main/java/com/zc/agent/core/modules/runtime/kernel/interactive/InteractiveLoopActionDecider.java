package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.math.BigDecimal;

/** Interactive Loop 的模型 Action 决策边界。 */
public interface InteractiveLoopActionDecider {

    /** 在物理 LLM 调用前估算本次 Token 和成本，由控制器先占用预算。 */
    ProjectedUsage estimate(InteractiveLoopActionRequest request);

    /** 调用模型并返回经过严格解析的结构化 Action。 */
    InteractiveLoopAction decide(InteractiveLoopActionRequest request) throws Exception;

    /**
     * @param tokens 预计输入与最大输出 Token 总量
     * @param costAmount 按当前模型价格估算的成本
     */
    record ProjectedUsage(long tokens, BigDecimal costAmount) {
        public ProjectedUsage {
            if (tokens < 0 || costAmount == null || costAmount.signum() < 0) {
                throw new IllegalArgumentException("Interactive Loop 预计用量不能为负数");
            }
        }
    }
}
