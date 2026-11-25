package com.zc.agent.core.modules.tool.dto;

import java.util.Map;

/**
 * Tool 调试请求。
 *
 * @param connectivityOnly 是否只做连通性测试
 * @param inputText        调试输入文本
 * @param arguments        调试参数
 */
public record ToolDebugRequest(
        Boolean connectivityOnly,
        String inputText,
        Map<String, Object> arguments
) {
}
