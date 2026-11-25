package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可配置搜索 Provider 的 Web Search Tool，返回 Provider 原始结果供后续节点归纳。
 */
@Component
public class WebSearchToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "WEB_SEARCH";
    private final SafeHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchToolExecutor(SafeHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String query = BuiltinToolSupport.text(request, "query", request.toolInputText());
        if (query == null || query.isBlank()) {
            throw BuiltinToolSupport.failure("搜索关键词不能为空", "WEB_SEARCH_QUERY_REQUIRED", Map.of());
        }
        JsonNode invokeConfig = BuiltinToolSupport.objectConfig(tool.invokeConfig());
        String template = invokeConfig == null ? null : invokeConfig.path("urlTemplate").asText(null);
        if (template == null || !template.contains("{query}")) {
            throw BuiltinToolSupport.failure("WEB_SEARCH 尚未配置有效 urlTemplate", "WEB_SEARCH_NOT_CONFIGURED", Map.of());
        }
        int limit = BuiltinToolSupport.integer(request, "limit", 5, 1, 20);
        String url = template.replace("{query}", URLEncoder.encode(query.trim(), StandardCharsets.UTF_8))
                .replace("{limit}", String.valueOf(limit));
        SafeHttpClient.FetchResponse response = httpClient.get(tool, url,
                Map.of("Accept", "application/json", "User-Agent", "AgentPlatform/1.0"), 524288);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw BuiltinToolSupport.failure("搜索 Provider 返回状态码 " + response.statusCode(),
                    "WEB_SEARCH_PROVIDER_ERROR", Map.of("statusCode", response.statusCode()));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("query", query.trim());
        snapshot.put("limit", limit);
        snapshot.put("providerResponse", parseJson(response.body()));
        snapshot.put("contentType", response.contentType());
        return new ToolExecutionResult(tool.toolCode(), tool.toolName(), "Web 搜索完成", response.body(),
                BuiltinToolSupport.json(objectMapper, snapshot));
    }

    private Object parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return value;
        }
    }
}
