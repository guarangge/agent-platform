package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.repository.ToolRuntimeGovernanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Tool 部署实例级熔断、半开探测与自动恢复服务。 */
@Service
public class ToolCircuitBreakerService {

    private final ToolRuntimeGovernanceRepository repository;

    public ToolCircuitBreakerService(ToolRuntimeGovernanceRepository repository) {
        this.repository = repository;
    }

    public boolean allow(ExecutionContext context, RegistryAgentTool tool) {
        Route route = route(tool);
        if (route.deploymentId() == null) {
            return true;
        }
        var state = repository.findState(route.deploymentId()).orElse(null);
        if (state == null || "closed".equals(state.circuitState()) || "half_open".equals(state.circuitState())) {
            return true;
        }
        if (state.nextProbeAt() != null && !state.nextProbeAt().isAfter(LocalDateTime.now())) {
            repository.markHalfOpen(route.deploymentId());
            audit(context, tool, route, "circuit_half_open", "success", null, "进入半开恢复探测");
            return true;
        }
        audit(context, tool, route, "circuit_skip", "rejected", "TOOL_CIRCUIT_OPEN", "实例熔断中，跳过路由");
        return false;
    }

    public void success(ExecutionContext context, RegistryAgentTool tool) {
        Route route = route(tool);
        if (route.deploymentId() == null) {
            return;
        }
        repository.recordSuccess(route.deploymentId(), tool.toolId(), route.deploymentCode());
        audit(context, tool, route, "invoke", "success", null, "实例调用成功，熔断状态已关闭");
    }

    public void failure(ExecutionContext context, RegistryAgentTool tool, ToolExecutionException exception) {
        Route route = route(tool);
        if (route.deploymentId() == null) {
            return;
        }
        int failures = repository.recordFailure(route.deploymentId(), tool.toolId(), route.deploymentCode(),
                exception.getMessage(), route.failureThreshold(), route.recoverySeconds());
        String action = failures >= route.failureThreshold() ? "circuit_open" : "invoke";
        audit(context, tool, route, action, "failed", exception.errorCode(), exception.getMessage());
    }

    private void audit(ExecutionContext context, RegistryAgentTool tool, Route route, String action,
                       String result, String errorCode, String message) {
        repository.insertAudit(tool.toolId(), route.deploymentId(), tool.toolCode(), route.deploymentCode(),
                action, result, context.taskContext().traceId(), errorCode, message, "{}");
    }

    private Route route(RegistryAgentTool tool) {
        JsonNode node = tool.metadata() == null ? null : tool.metadata().path("lifecycleRoute");
        if (node == null || node.isMissingNode()) {
            return new Route(null, tool.providerInstanceCode(), 3, 60);
        }
        return new Route(node.path("deploymentId").isNumber() ? node.path("deploymentId").asLong() : null,
                node.path("deploymentCode").asText(tool.providerInstanceCode()),
                node.path("failureThreshold").asInt(3), node.path("recoverySeconds").asInt(60));
    }

    private record Route(Long deploymentId, String deploymentCode, int failureThreshold, int recoverySeconds) {
    }
}
