package com.zc.agent.core.modules.runtime.replay.model;

import java.util.Map;

/**
 * 已创建的独立 Replay 实例。
 *
 * @param replayCode Replay 编码
 * @param replayTraceId 独立 Replay Trace ID
 * @param replayMode Replay 模式
 * @param manifest 不可变 Replay Manifest
 */
public record ReplayInstance(
        String replayCode,
        String replayTraceId,
        ReplayMode replayMode,
        Map<String, Object> manifest
) {
}
