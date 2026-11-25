package com.zc.agent.core.modules.runtime.model;

import com.zc.agent.core.modules.cost.service.BudgetExceededException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** 单次 Agent Run 共享的 LLM 物理调用预算，可安全跨并行步骤共享。 */
public final class LlmCallBudget {
    private final int maximumCalls;
    private final AtomicInteger consumedCalls = new AtomicInteger();

    public LlmCallBudget(int maximumCalls) {
        this.maximumCalls = Math.max(1, maximumCalls);
    }

    /** 在真正请求模型前占用一次调用额度。 */
    public int consume() {
        int current = consumedCalls.incrementAndGet();
        if (current > maximumCalls) {
            throw new BudgetExceededException("单次 Agent 运行 LLM 调用次数超过上限 " + maximumCalls
                    + "，已阻止第 " + current + " 次调用");
        }
        return current;
    }

    public int maximumCalls() {
        return maximumCalls;
    }

    public int consumedCalls() {
        return consumedCalls.get();
    }

    public Map<String, Object> snapshot() {
        return Map.of("maximumCalls", maximumCalls, "consumedCalls", consumedCalls());
    }
}
