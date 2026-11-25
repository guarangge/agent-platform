package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.dto.ToolDebugRequest;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolHealthCheckRecord;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import com.zc.agent.core.modules.tool.repository.ToolHealthCheckRepository;
import com.zc.agent.core.modules.tool.vo.ToolDebugResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tool 调试服务。
 */
@Service
public class ToolDebugService {

    private static final Logger log = LoggerFactory.getLogger(ToolDebugService.class);

    private final RegistryAgentClient registryAgentClient;
    private final ToolRouterService toolRouterService;
    private final McpToolProviderAdapter mcpToolProviderAdapter;
    private final List<ToolExecutor> toolExecutors;
    private final ToolHealthCheckRepository toolHealthCheckRepository;
    private final ObjectMapper objectMapper;

    public ToolDebugService(RegistryAgentClient registryAgentClient,
                            ToolRouterService toolRouterService,
                            McpToolProviderAdapter mcpToolProviderAdapter,
                            List<ToolExecutor> toolExecutors,
                            ToolHealthCheckRepository toolHealthCheckRepository,
                            ObjectMapper objectMapper) {
        this.registryAgentClient = registryAgentClient;
        this.toolRouterService = toolRouterService;
        this.mcpToolProviderAdapter = mcpToolProviderAdapter;
        this.toolExecutors = toolExecutors;
        this.toolHealthCheckRepository = toolHealthCheckRepository;
        this.objectMapper = objectMapper;
    }

    public ToolDebugResponse debugTool(Long toolId, ToolDebugRequest request, String authorization) {
        RegistryAgentTool tool = registryAgentClient.getRuntimeToolDetail(toolId, authorization);
        try {
            boolean connectivityOnly = request == null || request.connectivityOnly() == null || request.connectivityOnly();
            if (connectivityOnly) {
                return connectivityProbe(tool);
            }
            ToolExecutionResult result = toolRouterService.executeResolvedTool(
                    buildDebugExecutionContext(tool),
                    tool,
                    new ToolInvocationRequest(
                            request.inputText() == null ? "" : request.inputText(),
                            request.arguments() == null ? Map.of() : request.arguments()
                    )
            );
            return new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    tool.providerType(),
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    "healthy",
                    true,
                    "Tool 调试执行成功",
                    Map.of(
                            "summary", result.summary(),
                            "responseText", result.responseText(),
                            "outputSnapshot", result.outputSnapshot()
                    )
            );
        } catch (Exception exception) {
            log.warn("Tool 调试执行失败, toolId={}, errorType={}, reason={}", toolId,
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            return new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    tool.providerType(),
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    "unhealthy",
                    false,
                    exception.getMessage() == null ? "Tool 调试失败" : exception.getMessage(),
                    Map.of()
            );
        }
    }

    public ToolDebugResponse healthCheck(Long toolId, String authorization) {
        RegistryAgentTool tool = registryAgentClient.getRuntimeToolDetail(toolId, authorization);
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            ToolDebugResponse response = connectivityProbe(tool);
            recordHealthCheck(tool, response, startedAt);
            return response;
        } catch (Exception exception) {
            ToolDebugResponse failedResponse = new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    tool.providerType(),
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    "unhealthy",
                    false,
                    exception.getMessage() == null ? "健康检查失败" : exception.getMessage(),
                    Map.of()
            );
            recordHealthCheck(tool, failedResponse, startedAt);
            return failedResponse;
        }
    }

    private ToolDebugResponse connectivityProbe(RegistryAgentTool tool) {
        if ("internal".equals(tool.providerType()) || tool.providerType() == null || tool.providerType().isBlank()) {
            boolean supported = toolExecutors.stream().anyMatch(item -> item.supports(tool.toolCode()));
            return new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    "internal",
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    supported ? "healthy" : "unhealthy",
                    supported,
                    supported ? "本地 Tool 执行器已注册" : "未找到本地 Tool 执行器",
                    Map.of("toolCode", tool.toolCode())
            );
        }
        if ("mcp".equals(tool.providerType())) {
            Map<String, Object> output = mcpToolProviderAdapter.testConnectivity(tool);
            return new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    "mcp",
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    "healthy",
                    true,
                    "MCP 初始化成功",
                    output
            );
        }
        if ("external_http".equals(tool.providerType())) {
            Map<String, Object> output = probeExternalHttp(tool);
            return new ToolDebugResponse(
                    tool.toolCode(),
                    tool.toolName(),
                    "external_http",
                    tool.transportType(),
                    resolveSchemaVersion(tool),
                    "healthy",
                    true,
                    "外部 HTTP Tool 连通性测试成功",
                    output
            );
        }
        return new ToolDebugResponse(
                tool.toolCode(),
                tool.toolName(),
                tool.providerType(),
                tool.transportType(),
                resolveSchemaVersion(tool),
                "unknown",
                false,
                "未知 Provider 类型",
                Map.of()
        );
    }

    private ExecutionContext buildDebugExecutionContext(RegistryAgentTool tool) {
        return new ExecutionContext(
                new ExecutionContext.CurrentUserContext(0L, "tool-debug", "Tool Debug", List.of(), List.of()),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                new ExecutionContext.ConversationContext(0L, "TOOL_DEBUG", "Tool Debug", "processing", null),
                new ExecutionContext.AgentContext(0L, "TOOL_DEBUG_AGENT", "Tool Debug Agent", null, "enabled", null),
                null,
                null,
                null,
                new ExecutionContext.TimeContext(null, null, null),
                null,
                Map.of(),
                new ExecutionContext.MessageContext(0L, ""),
                new ExecutionContext.TaskContext(0L, "TOOL_DEBUG_TASK", "TOOL_DEBUG_TRACE_" + tool.toolId()),
                List.of(tool),
                List.of(),
                List.of()
        );
    }

    private Map<String, Object> probeExternalHttp(RegistryAgentTool tool) {
        if (tool.endpoint() == null || tool.endpoint().isBlank()) {
            throw new IllegalStateException("未配置 endpoint");
        }
        String healthPath = tool.invokeConfig() == null ? null : tool.invokeConfig().path("healthPath").asText(null);
        String healthMethod = tool.invokeConfig() == null ? "HEAD" : tool.invokeConfig().path("healthMethod").asText("HEAD");
        String url = tool.endpoint();
        if (healthPath != null && !healthPath.isBlank()) {
            url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            url = url + (healthPath.startsWith("/") ? healthPath : "/" + healthPath);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(tool.providerTimeoutSeconds() == null || tool.providerTimeoutSeconds() <= 0 ? 10 : tool.providerTimeoutSeconds()));
        if ("GET".equalsIgnoreCase(healthMethod)) {
            builder.GET();
        } else {
            builder.method(healthMethod.toUpperCase(), HttpRequest.BodyPublishers.noBody());
        }
        ToolInvocationSupport.resolveHeaders(tool, new com.fasterxml.jackson.databind.ObjectMapper()).forEach(builder::header);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(tool.providerTimeoutSeconds() == null || tool.providerTimeoutSeconds() <= 0 ? 10 : tool.providerTimeoutSeconds()))
                    .build();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IllegalStateException("status=" + response.statusCode());
            }
            return Map.of(
                    "endpoint", url,
                    "statusCode", response.statusCode(),
                    "transportType", tool.transportType() == null ? "" : tool.transportType(),
                    "providerStatus", tool.status() == null ? "" : tool.status(),
                    "operationType", tool.operationType() == null ? "" : tool.operationType(),
                    "operationName", tool.operationName() == null ? "" : tool.operationName()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP 连接失败: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 连通性测试被中断", exception);
        }
    }

    private String resolveSchemaVersion(RegistryAgentTool tool) {
        if (tool.metadata() != null && tool.metadata().hasNonNull("schemaVersion")) {
            return tool.metadata().path("schemaVersion").asText("");
        }
        if (tool.invokeConfig() != null && tool.invokeConfig().hasNonNull("schemaVersion")) {
            return tool.invokeConfig().path("schemaVersion").asText("");
        }
        return "v1";
    }

    private void recordHealthCheck(RegistryAgentTool tool, ToolDebugResponse response, LocalDateTime startedAt) {
        LocalDateTime checkedAt = LocalDateTime.now();
        toolHealthCheckRepository.insert(
                new ToolHealthCheckRecord(
                        null,
                        tool.toolId(),
                        tool.toolCode(),
                        tool.toolName(),
                        tool.providerType(),
                        tool.transportType(),
                        tool.providerCode(),
                        tool.providerName(),
                        tool.providerInstanceCode(),
                        tool.providerInstanceName(),
                        tool.environmentCode(),
                        tool.endpoint(),
                        tool.operationName(),
                        tool.operationType(),
                        response.healthStatus(),
                        response.success(),
                        response.message(),
                        response.success() ? "" : response.message(),
                        extractStatusCode(response.output()),
                        java.time.temporal.ChronoUnit.MILLIS.between(startedAt, checkedAt),
                        toJsonText(response.output()),
                        checkedAt
                ),
                0L
        );
    }

    private Integer extractStatusCode(Map<String, Object> output) {
        Object value = output == null ? null : output.get("statusCode");
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toJsonText(Map<String, Object> output) {
        try {
            return objectMapper.writeValueAsString(output == null ? Map.of() : output);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
