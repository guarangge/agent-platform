package com.zc.agent.core.modules.runtime.model;

import java.util.Arrays;
import java.util.Optional;

/** Agent 运行时支持的执行模式。 */
public enum RuntimeExecutionMode {

    WORKFLOW_PLAN("workflow_plan"),
    INTERACTIVE_LOOP("interactive_loop");

    private final String code;

    RuntimeExecutionMode(String code) {
        this.code = code;
    }

    /** 返回稳定协议编码。 */
    public String code() {
        return code;
    }

    /** 安全解析执行模式。 */
    public static Optional<RuntimeExecutionMode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code.trim()))
                .findFirst();
    }
}
