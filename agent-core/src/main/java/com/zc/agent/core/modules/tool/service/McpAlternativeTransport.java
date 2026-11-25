package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;

import java.util.Map;

/**
 * MCP 非 Streamable HTTP 传输扩展点。
 */
public interface McpAlternativeTransport {

    /**
     * 判断是否支持指定传输类型。
     */
    boolean supports(String transportType);

    /**
     * 执行 tools/call。
     */
    McpTransportCallResult call(RegistryAgentTool tool, Map<String, Object> arguments);

    /**
     * 初始化并执行 tools/list 健康检查。
     */
    Map<String, Object> testConnectivity(RegistryAgentTool tool);

    /**
     * MCP 传输调用结果。
     *
     * @param rpcResponse JSON-RPC 响应
     * @param rawResponse 原始响应
     * @param metadata    传输元数据
     */
    record McpTransportCallResult(JsonNode rpcResponse, String rawResponse, Map<String, Object> metadata) {
    }
}
