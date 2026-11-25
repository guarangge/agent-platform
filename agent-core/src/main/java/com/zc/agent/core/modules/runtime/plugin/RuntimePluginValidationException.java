package com.zc.agent.core.modules.runtime.plugin;

import java.util.Map;

/** Runtime Plugin 启动校验失败，阻断存在歧义或不兼容的插件进入运行时。 */
public class RuntimePluginValidationException extends IllegalStateException {

    private final String errorCode;
    private final Map<String, Object> details;

    public RuntimePluginValidationException(String message,
                                            String errorCode,
                                            Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** 返回稳定错误编码。 */
    public String errorCode() {
        return errorCode;
    }

    /** 返回不包含插件密钥或参数正文的安全错误详情。 */
    public Map<String, Object> details() {
        return details;
    }
}
