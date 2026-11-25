package com.zc.agent.core.modules.tool.model;

import java.util.Map;

/**
 * Tool 调用请求。
 *
 * @param toolInputText 归一化后的文本输入
 * @param arguments    结构化参数
 */
public record ToolInvocationRequest(
        String toolInputText,
        Map<String, Object> arguments
) {
}
