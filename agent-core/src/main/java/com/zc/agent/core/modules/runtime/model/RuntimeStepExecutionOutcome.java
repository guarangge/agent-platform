package com.zc.agent.core.modules.runtime.model;

/**
 * 单个 Plan 或 Workflow 节点的稳定运行时执行结果。
 *
 * @param responseText   面向后续步骤或用户的响应文本
 * @param outputSnapshot 标准输出快照
 * @param summary        执行摘要
 */
public record RuntimeStepExecutionOutcome(
        String responseText,
        String outputSnapshot,
        String summary
) {
}
