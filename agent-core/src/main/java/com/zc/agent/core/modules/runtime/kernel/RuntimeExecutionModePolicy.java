package com.zc.agent.core.modules.runtime.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionMode;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 解析 Agent 双执行模式，并在确定性 Workflow、审批或未开放动态循环时安全降级。
 */
@Service
public class RuntimeExecutionModePolicy {

    private final InteractiveLoopRolloutPolicy interactiveLoopRolloutPolicy;

    public RuntimeExecutionModePolicy(InteractiveLoopRolloutPolicy interactiveLoopRolloutPolicy) {
        this.interactiveLoopRolloutPolicy = interactiveLoopRolloutPolicy;
    }

    /** 返回本次 Run 的请求模式、实际模式和可审计原因。 */
    public Selection select(AgentLoopExecutionRequest kernelRequest) {
        RuntimeExecutionRequest request = kernelRequest == null ? null : kernelRequest.executionRequest();
        ModeConfiguration configuration = resolveConfiguration(request == null ? null : request.executionContext());
        RuntimeExecutionMode requestedMode = configuration.defaultMode();
        if (!configuration.allowedModes().contains(requestedMode)) {
            return fallback(requestedMode, configuration.allowedModes(), "mode_not_allowed");
        }
        if (hasWorkflowSelection(request)) {
            return fallback(requestedMode, configuration.allowedModes(), "workflow_selected");
        }
        if (hasApprovalStep(request)) {
            return fallback(requestedMode, configuration.allowedModes(), "approval_required");
        }
        if (requestedMode == RuntimeExecutionMode.INTERACTIVE_LOOP) {
            InteractiveLoopRolloutPolicy.Selection rollout = interactiveLoopRolloutPolicy.select(kernelRequest);
            if (!rollout.enabled()) {
                return fallback(requestedMode, configuration.allowedModes(), rollout.reason(), rollout.rolloutBucket());
            }
            return new Selection(requestedMode, requestedMode, configuration.allowedModes(),
                    rollout.reason(), false, rollout.rolloutBucket());
        }
        return new Selection(requestedMode, requestedMode, configuration.allowedModes(), "agent_default", false, null);
    }

    private ModeConfiguration resolveConfiguration(ExecutionContext context) {
        JsonNode metadata = context == null || context.agentContext() == null
                ? null : context.agentContext().metadata();
        JsonNode policy = metadata == null ? null : metadata.path("executionPolicy");
        RuntimeExecutionMode defaultMode = RuntimeExecutionMode.fromCode(
                        policy == null ? null : policy.path("defaultMode").asText(null))
                .orElse(RuntimeExecutionMode.WORKFLOW_PLAN);
        LinkedHashSet<RuntimeExecutionMode> allowedModes = new LinkedHashSet<>();
        // Core 不能完全信任历史或绕过 Registry 写入的数据，始终保留确定性安全路径。
        allowedModes.add(RuntimeExecutionMode.WORKFLOW_PLAN);
        JsonNode allowedModesNode = policy == null ? null : policy.path("allowedModes");
        if (allowedModesNode != null && allowedModesNode.isArray()) {
            allowedModesNode.forEach(node -> RuntimeExecutionMode.fromCode(node.asText(null))
                    .ifPresent(allowedModes::add));
        }
        return new ModeConfiguration(defaultMode, List.copyOf(allowedModes));
    }

    private boolean hasWorkflowSelection(RuntimeExecutionRequest request) {
        return request != null && request.executionDecision() != null
                && request.executionDecision().hasWorkflowSelection();
    }

    private boolean hasApprovalStep(RuntimeExecutionRequest request) {
        ExecutionPlan plan = request == null ? null : request.executionPlan();
        return plan != null && plan.steps() != null && plan.steps().stream()
                .anyMatch(step -> step != null && "approval".equals(step.stepType()));
    }

    private Selection fallback(RuntimeExecutionMode requestedMode,
                               List<RuntimeExecutionMode> allowedModes,
                               String reason) {
        return fallback(requestedMode, allowedModes, reason, null);
    }

    private Selection fallback(RuntimeExecutionMode requestedMode,
                               List<RuntimeExecutionMode> allowedModes,
                               String reason,
                               Integer rolloutBucket) {
        return new Selection(requestedMode, RuntimeExecutionMode.WORKFLOW_PLAN,
                allowedModes, reason, requestedMode != RuntimeExecutionMode.WORKFLOW_PLAN, rolloutBucket);
    }

    private record ModeConfiguration(RuntimeExecutionMode defaultMode,
                                     List<RuntimeExecutionMode> allowedModes) {
    }

    /**
     * @param requestedMode Agent 请求的默认模式
     * @param effectiveMode 本次 Run 实际执行模式
     * @param allowedModes Agent 允许的执行模式
     * @param reason 确定性路由原因
     * @param fallback 是否发生安全回退
     * @param rolloutBucket Interactive Loop 稳定灰度分桶
     */
    public record Selection(RuntimeExecutionMode requestedMode,
                            RuntimeExecutionMode effectiveMode,
                            List<RuntimeExecutionMode> allowedModes,
                            String reason,
                            boolean fallback,
                            Integer rolloutBucket) {
    }
}
