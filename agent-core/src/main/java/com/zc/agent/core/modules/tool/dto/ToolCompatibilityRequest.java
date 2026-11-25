package com.zc.agent.core.modules.tool.dto;
import java.util.Map;
/** @param inputText 调用样例文本 @param arguments 调用样例参数 */
public record ToolCompatibilityRequest(String inputText, Map<String,Object> arguments) {}
