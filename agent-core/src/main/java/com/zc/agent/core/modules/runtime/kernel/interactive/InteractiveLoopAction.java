package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.util.Map;

/**
 * Interactive Loop 结构化 Action，不包含模型 rationale 或思维链。
 *
 * @param type Action 类型
 * @param toolCode Tool Action 的目标编码
 * @param arguments Tool Action 的结构化参数
 * @param content Final Action 的最终回复
 */
public record InteractiveLoopAction(
        Type type,
        String toolCode,
        Map<String, Object> arguments,
        String content
) {

    /** 支持的最小 Action 类型。 */
    public enum Type {
        TOOL_CALL("tool_call"),
        FINAL_ANSWER("final_answer");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        /** 返回稳定协议编码。 */
        public String code() {
            return code;
        }

        /** 严格解析 Action 类型。 */
        public static Type fromCode(String code) {
            for (Type value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("不支持的 Interactive Loop Action: " + code);
        }
    }
}
