package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 受控公共网络内容读取 Tool，仅提供无副作用 GET 请求。
 */
@Component
public class HttpFetchToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "HTTP_FETCH";
    private final SafeHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpFetchToolExecutor(SafeHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String url = BuiltinToolSupport.requiredText(request, "url");
        int maxBytes = BuiltinToolSupport.integer(request, "maxBytes", 262144, 1024, 1048576);
        SafeHttpClient.FetchResponse response = httpClient.get(tool, url, Map.of(
                "Accept", BuiltinToolSupport.text(request, "accept", "text/plain,text/html,application/json"),
                "User-Agent", "AgentPlatform/1.0"
        ), maxBytes);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("statusCode", response.statusCode());
        snapshot.put("url", response.url());
        snapshot.put("contentType", response.contentType());
        snapshot.put("body", response.body());
        snapshot.put("bodyBytes", response.bodyBytes());
        String summary = "HTTP GET 完成，状态码 " + response.statusCode();
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(), summary, response.body(),
                BuiltinToolSupport.json(objectMapper, snapshot));
    }
}
