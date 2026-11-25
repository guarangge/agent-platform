package com.zc.agent.core.modules.runtime.replay.model;

import java.util.Arrays;

/** Runtime Replay 模式契约。 */
public enum ReplayMode {

    PROMPT_REPLAY("prompt_replay", true),
    SIMULATION_REPLAY("simulation_replay", true),
    EXECUTION_REPLAY("execution_replay", false);

    private final String code;
    private final boolean enabled;

    ReplayMode(String code, boolean enabled) {
        this.code = code;
        this.enabled = enabled;
    }

    /** 返回对外稳定编码。 */
    public String code() {
        return code;
    }

    /** 返回当前阶段是否已开放执行入口。 */
    public boolean enabled() {
        return enabled;
    }

    /** 按稳定编码解析 Replay Mode。 */
    public static ReplayMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知 Replay Mode: " + code));
    }
}
