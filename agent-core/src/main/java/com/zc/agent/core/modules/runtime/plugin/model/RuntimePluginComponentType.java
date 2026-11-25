package com.zc.agent.core.modules.runtime.plugin.model;

import java.util.Arrays;

/** Runtime Plugin 首批支持的组件类型。 */
public enum RuntimePluginComponentType {

    TOOLS("tools"),
    TOOL_PROVIDERS("toolProviders"),
    STEP_HANDLERS("stepHandlers"),
    CONTEXT_PROVIDERS("contextProviders"),
    POLICIES("policies"),
    HOOKS("hooks"),
    SKILLS("skills");

    private final String code;

    RuntimePluginComponentType(String code) {
        this.code = code;
    }

    /** 返回 Manifest 使用的稳定类型编码。 */
    public String code() {
        return code;
    }

    /**
     * 校验并返回组件类型。
     *
     * @param code Manifest 组件类型编码
     * @return 标准组件类型
     */
    public static RuntimePluginComponentType require(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的 Runtime Plugin 组件类型: " + code));
    }
}
