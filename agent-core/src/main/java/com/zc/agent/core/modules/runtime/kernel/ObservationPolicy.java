package com.zc.agent.core.modules.runtime.kernel;

import java.util.Map;

/** 可替换的步骤后 observation 决策策略。 */
public interface ObservationPolicy {

    /** 返回稳定策略编码，用于 Trace 和后续插件路由。 */
    String policyCode();

    /** 评估当前步骤输出并返回循环控制决策。 */
    Decision evaluate(ObservationPolicyRequest request);

    /**
     * @param action 后续动作：continue / replan / finish
     * @param reason 决策原因
     * @param metadata 可观测决策元数据
     */
    record Decision(
            String action,
            String reason,
            Map<String, Object> metadata
    ) {
    }
}
