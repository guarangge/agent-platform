package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 响应解析器，同时支持 application/json 与 Streamable HTTP SSE 响应。
 */
@Component
public class McpJsonRpcResponseParser {

    private final ObjectMapper objectMapper;

    public McpJsonRpcResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 提取指定 requestId 对应的 JSON-RPC 响应并校验协议错误。
     */
    public JsonNode parse(String body, String contentType, String requestId, String toolCode) {
        try {
            List<JsonNode> messages = isEventStream(contentType, body)
                    ? parseEventStream(body)
                    : List.of(objectMapper.readTree(body));
            JsonNode response = messages.stream()
                    .filter(item -> requestId.equals(item.path("id").asText()))
                    .findFirst()
                    .orElseThrow(() -> protocolError("MCP 响应缺少匹配的 requestId", toolCode, Map.of("requestId", requestId)));
            if (!"2.0".equals(response.path("jsonrpc").asText())) {
                throw protocolError("MCP 响应 jsonrpc 版本无效", toolCode, Map.of("requestId", requestId));
            }
            JsonNode error = response.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                int rpcCode = error.path("code").asInt(0);
                String message = error.path("message").asText("MCP JSON-RPC 调用失败");
                throw new ToolExecutionException(
                        "MCP JSON-RPC 错误，code=" + rpcCode + ", message=" + message,
                        "MCP_JSON_RPC_ERROR", "provider_protocol", rpcCode <= -32000 && rpcCode >= -32099,
                        null, Map.of("toolCode", toolCode, "rpcCode", rpcCode, "rpcMessage", message)
                );
            }
            if (response.path("result").isMissingNode()) {
                throw protocolError("MCP 响应缺少 result", toolCode, Map.of("requestId", requestId));
            }
            return response;
        } catch (ToolExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw protocolError("MCP 响应解析失败: " + exception.getMessage(), toolCode, Map.of("requestId", requestId));
        }
    }

    private List<JsonNode> parseEventStream(String body) throws Exception {
        ArrayList<JsonNode> messages = new ArrayList<>();
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R", -1)) {
            if (line.isBlank()) {
                appendEvent(messages, data);
                data.setLength(0);
                continue;
            }
            if (line.startsWith("data:")) {
                if (data.length() > 0) data.append('\n');
                data.append(line.substring(5).stripLeading());
            }
        }
        appendEvent(messages, data);
        return messages;
    }

    private void appendEvent(List<JsonNode> messages, StringBuilder data) throws Exception {
        if (data.length() > 0) {
            messages.add(objectMapper.readTree(data.toString()));
        }
    }

    private boolean isEventStream(String contentType, String body) {
        return (contentType != null && contentType.toLowerCase().contains("text/event-stream"))
                || (body != null && body.stripLeading().startsWith("data:"));
    }

    private ToolExecutionException protocolError(String message, String toolCode, Map<String, Object> details) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("toolCode", toolCode);
        metadata.putAll(details);
        return new ToolExecutionException(message, "MCP_PROTOCOL_ERROR", "provider_protocol", false, null,
                metadata);
    }
}
