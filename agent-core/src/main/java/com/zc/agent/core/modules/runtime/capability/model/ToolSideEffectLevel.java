package com.zc.agent.core.modules.runtime.capability.model;

import java.util.Set;

/**
 * Tool 副作用等级，用于约束重试、幂等与补偿行为。
 */
public enum ToolSideEffectLevel {

    NONE("none"),
    IDEMPOTENT("idempotent"),
    COMPENSATABLE("compensatable"),
    IRREVERSIBLE("irreversible");

    private static final Set<String> SUPPORTED_CODES = Set.of(
            NONE.code, IDEMPOTENT.code, COMPENSATABLE.code, IRREVERSIBLE.code);

    private final String code;

    ToolSideEffectLevel(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 校验并规范化副作用等级。
     */
    public static String require(String level) {
        if (level == null || level.isBlank()) {
            throw new IllegalArgumentException("Tool Side Effect Level 不能为空");
        }
        String normalized = level.trim();
        if (!SUPPORTED_CODES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的 Tool Side Effect Level: " + normalized);
        }
        return normalized;
    }
}
