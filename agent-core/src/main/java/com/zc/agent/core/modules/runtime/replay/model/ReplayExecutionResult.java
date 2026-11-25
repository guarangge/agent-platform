package com.zc.agent.core.modules.runtime.replay.model;

import java.util.List;
import java.util.Map;

/**
 * Replay Engine 的独立执行结果。
 *
 * @param output 纯内存重组结果
 * @param injectedObservations 实际注入的录制观察
 */
public record ReplayExecutionResult(
        Map<String, Object> output,
        List<Map<String, Object>> injectedObservations
) {
}
