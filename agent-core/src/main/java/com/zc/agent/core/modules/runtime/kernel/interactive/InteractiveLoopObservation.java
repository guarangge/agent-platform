package com.zc.agent.core.modules.runtime.kernel.interactive;

/**
 * Tool Action 产生的模型可见 Observation。
 *
 * @param iteration 产生 Observation 的迭代序号
 * @param toolCode Tool 编码
 * @param status success / unavailable / failed
 * @param summary 有界结果摘要
 * @param responseText 有界结果文本
 */
public record InteractiveLoopObservation(
        int iteration,
        String toolCode,
        String status,
        String summary,
        String responseText
) {
}
