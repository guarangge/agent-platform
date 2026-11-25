package com.zc.agent.core.modules.cost.service;

import com.zc.agent.core.common.exception.BusinessException;

/** 预算硬限制异常。该异常不可被 LLM 降级、规则回退或模板回复吞掉。 */
public class BudgetExceededException extends BusinessException {
    public BudgetExceededException(String message) { super(message); }

    /** 如果异常链包含预算硬限制，则立即恢复原始业务异常语义。 */
    public static void propagate(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BudgetExceededException exceeded) throw exceeded;
            current = current.getCause();
        }
    }
}
