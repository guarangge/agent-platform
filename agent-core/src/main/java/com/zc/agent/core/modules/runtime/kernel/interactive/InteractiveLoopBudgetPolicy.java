package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.math.BigDecimal;

/**
 * Interactive Loop 单次 Run 的局部硬预算。
 * 全局 CostGovernance 和 Run LLM 预算仍作为更外层限制，实际执行取更严格结果。
 *
 * @param maxIterations 最大循环迭代数
 * @param maxLlmCalls 最大 LLM 调用数
 * @param maxToolCalls 最大 Tool 调用数
 * @param maxTokens 最大预计 Token 数
 * @param maxCostAmount 最大预计成本
 * @param duplicateToolCallLimit 相同 Tool 与参数指纹允许调用次数
 */
public record InteractiveLoopBudgetPolicy(
        int maxIterations,
        int maxLlmCalls,
        int maxToolCalls,
        long maxTokens,
        BigDecimal maxCostAmount,
        int duplicateToolCallLimit
) {

    public InteractiveLoopBudgetPolicy {
        if (maxIterations < 1 || maxLlmCalls < 1 || maxToolCalls < 1 || maxTokens < 1
                || maxCostAmount == null || maxCostAmount.signum() < 0 || duplicateToolCallLimit < 1) {
            throw new IllegalArgumentException("Interactive Loop 预算必须为有效正数，成本上限允许为 0");
        }
        maxCostAmount = maxCostAmount.stripTrailingZeros();
    }
}
