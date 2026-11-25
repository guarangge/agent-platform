package com.zc.agent.core.modules.tool.model;

/**
 * Tool 执行结果。
 *
 * @param toolCode       Tool 编码
 * @param toolName       Tool 名称
 * @param summary        执行摘要
 * @param responseText   返回文本
 * @param outputSnapshot 输出快照 JSON
 */
public record ToolExecutionResult(
        String toolCode,
        String toolName,
        String summary,
        String responseText,
        String outputSnapshot
) {
}
