package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从注册中心解析 Tool 版本部署和灰度权重，生成本次调用的实例候选。 */
@Service
public class ToolLifecycleRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ToolLifecycleRoutingService.class);

    private final RestClient registryRestClient;
    private final ObjectMapper objectMapper;

    public ToolLifecycleRoutingService(RestClient registryRestClient, ObjectMapper objectMapper) {
        this.registryRestClient = registryRestClient;
        this.objectMapper = objectMapper;
    }

    /** 展开 Tool 的已启用部署；注册中心不可用时兼容回退到原 Tool 快照。 */
    public List<RegistryAgentTool> expand(ExecutionContext context, List<RegistryAgentTool> tools) {
        List<WeightedTool> expanded = new ArrayList<>();
        for (RegistryAgentTool tool : tools) {
            RouteResolution resolution = fetchRoutes(context, tool);
            if (!resolution.managed()) {
                expanded.add(new WeightedTool(tool, 100, 100, 0D));
                continue;
            }
            for (RegistryAgentTool route : resolution.routes()) {
                JsonNode routeMetadata = route.metadata().path("lifecycleRoute");
                int rollout = routeMetadata.path("rolloutPercent").asInt(100);
                String deploymentCode = routeMetadata.path("deploymentCode").asText("");
                if (!inRollout(context, tool.toolCode(), deploymentCode, rollout)) {
                    continue;
                }
                int weight = routeMetadata.path("trafficWeight").asInt(100);
                if (weight <= 0) {
                    continue;
                }
                int priority = routeMetadata.path("priority").asInt(100);
                expanded.add(new WeightedTool(route, priority, weight,
                        rendezvousScore(context, tool.toolCode(), deploymentCode, weight)));
            }
        }
        return expanded.stream()
                .sorted(Comparator.comparingInt(WeightedTool::priority).thenComparingDouble(WeightedTool::score))
                .map(WeightedTool::tool)
                .toList();
    }

    private RouteResolution fetchRoutes(ExecutionContext context, RegistryAgentTool base) {
        if (base.toolId() == null || "internal".equalsIgnoreCase(base.providerType())) {
            return new RouteResolution(false, List.of());
        }
        try {
            ApiEnvelope<JsonNode> response = registryRestClient.get()
                    .uri("/tools/{toolId}/runtime-routing", base.toolId())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.code() != 200 || response.data() == null) {
                return new RouteResolution(false, List.of());
            }
            List<RegistryAgentTool> routes = new ArrayList<>();
            response.data().path("routes").forEach(route -> routes.add(toTool(base, route)));
            return new RouteResolution(response.data().path("managed").asBoolean(true), routes);
        } catch (Exception exception) {
            log.warn("读取 Tool 生命周期路由失败，回退到执行快照: toolCode={}, message={}",
                    base.toolCode(), exception.getMessage());
            return new RouteResolution(false, List.of());
        }
    }

    private RegistryAgentTool toTool(RegistryAgentTool base, JsonNode route) {
        ObjectNode metadata = base.metadata() == null || !base.metadata().isObject()
                ? objectMapper.createObjectNode() : base.metadata().deepCopy();
        ObjectNode lifecycle = metadata.putObject("lifecycleRoute");
        lifecycle.put("deploymentId", route.path("id").asLong());
        lifecycle.put("deploymentCode", route.path("deployment_code").asText());
        lifecycle.put("versionId", route.path("version_id").asLong());
        lifecycle.put("version", route.path("version").asText());
        lifecycle.put("trafficWeight", route.path("traffic_weight").asInt(100));
        lifecycle.put("rolloutPercent", route.path("rollout_percent").asInt(100));
        lifecycle.put("priority", route.path("priority").asInt(100));
        lifecycle.put("failureThreshold", route.path("failure_threshold").asInt(3));
        lifecycle.put("recoverySeconds", route.path("recovery_seconds").asInt(60));
        return new RegistryAgentTool(base.toolId(), base.toolCode(), base.toolName(), base.toolType(),
                base.toolCategory(), base.runtimeCapabilities(), base.sideEffectLevel(),
                valueOr(route, "version_input_schema", base.inputSchema()),
                valueOr(route, "version_output_schema", base.outputSchema()),
                route.path("provider_type").asText(base.providerType()),
                route.path("transport_type").asText(base.transportType()), base.providerCode(), base.providerName(),
                route.path("deployment_code").asText(base.providerInstanceCode()),
                route.path("deployment_name").asText(base.providerInstanceName()),
                route.path("environment_code").asText(base.environmentCode()),
                route.path("endpoint").isNull() ? null : route.path("endpoint").asText(base.endpoint()),
                route.path("auth_type").asText(base.authType()), valueOr(route, "auth_config", base.authConfig()),
                valueOr(route, "headers", base.headers()), route.path("timeout_seconds").asInt(base.providerTimeoutSeconds()),
                route.path("retry_times").asInt(base.retryTimes()), base.operationName(), base.operationType(),
                valueOr(route, "invoke_config", base.invokeConfig()), base.argumentMapping(), base.outputMapping(),
                base.permissionLevel(), base.timeoutSeconds(), base.status(), base.description(), metadata);
    }

    private JsonNode valueOr(JsonNode route, String field, JsonNode fallback) {
        JsonNode value = route.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value;
    }

    private boolean inRollout(ExecutionContext context, String toolCode, String deploymentCode, int percent) {
        if (percent >= 100) {
            return true;
        }
        if (percent <= 0) {
            return false;
        }
        String key = context.taskContext().traceId() + ':' + toolCode + ':' + deploymentCode;
        return Math.floorMod(key.hashCode(), 100) < percent;
    }

    private double rendezvousScore(ExecutionContext context, String toolCode, String deploymentCode, int weight) {
        String key = context.taskContext().traceId() + ':' + toolCode + ':' + deploymentCode;
        long hash = Integer.toUnsignedLong(key.hashCode());
        double uniform = Math.max(1e-9, (hash + 1D) / (0x1_0000_0000L + 1D));
        return -Math.log(uniform) / Math.max(1, weight);
    }

    private record WeightedTool(RegistryAgentTool tool, int priority, int weight, double score) {
    }

    private record RouteResolution(boolean managed, List<RegistryAgentTool> routes) {
    }

    private record ApiEnvelope<T>(int code, String message, T data) {
    }
}
