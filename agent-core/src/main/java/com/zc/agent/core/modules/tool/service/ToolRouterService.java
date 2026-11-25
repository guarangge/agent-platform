package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.runtime.capability.CapabilityApprovalContext;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyEngine;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyRuntimeEventService;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.service.ToolRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.PolicyViolationException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool 路由服务。
 */
@Service
public class ToolRouterService {

    private final List<ToolProviderAdapter> toolProviderAdapters;
    private final List<ToolInvocationHook> toolInvocationHooks;
    private final ToolSchemaValidationService toolSchemaValidationService;
    private final ToolSelectionService toolSelectionService;
    private final ToolLifecycleRoutingService toolLifecycleRoutingService;
    private final ToolCircuitBreakerService toolCircuitBreakerService;
    private final ToolFallbackPolicyService toolFallbackPolicyService;
    private final ObjectMapper objectMapper;
    private final ObservationRegistry observationRegistry;
    private final CostGovernanceService costGovernanceService;
    private final ToolSideEffectService toolSideEffectService;
    private final ToolRuntimeEventService toolRuntimeEventService;
    private final CapabilityPolicyEngine capabilityPolicyEngine;
    private final CapabilityPolicyRuntimeEventService capabilityPolicyRuntimeEventService;

    public ToolRouterService(List<ToolProviderAdapter> toolProviderAdapters,
                             List<ToolInvocationHook> toolInvocationHooks,
                             ToolSchemaValidationService toolSchemaValidationService,
                             ToolSelectionService toolSelectionService,
                             ToolLifecycleRoutingService toolLifecycleRoutingService,
                             ToolCircuitBreakerService toolCircuitBreakerService,
                             ToolFallbackPolicyService toolFallbackPolicyService,
                             ObjectMapper objectMapper,
                             ObservationRegistry observationRegistry,
                             CostGovernanceService costGovernanceService,
                             ToolSideEffectService toolSideEffectService,
                             ToolRuntimeEventService toolRuntimeEventService,
                             CapabilityPolicyEngine capabilityPolicyEngine,
                             CapabilityPolicyRuntimeEventService capabilityPolicyRuntimeEventService) {
        this.toolProviderAdapters = toolProviderAdapters;
        this.toolInvocationHooks = toolInvocationHooks;
        this.toolSchemaValidationService = toolSchemaValidationService;
        this.toolSelectionService = toolSelectionService;
        this.toolLifecycleRoutingService = toolLifecycleRoutingService;
        this.toolCircuitBreakerService = toolCircuitBreakerService;
        this.toolFallbackPolicyService = toolFallbackPolicyService;
        this.objectMapper = objectMapper;
        this.observationRegistry = observationRegistry;
        this.costGovernanceService = costGovernanceService;
        this.toolSideEffectService = toolSideEffectService;
        this.toolRuntimeEventService = toolRuntimeEventService;
        this.capabilityPolicyEngine = capabilityPolicyEngine;
        this.capabilityPolicyRuntimeEventService = capabilityPolicyRuntimeEventService;
    }

    /**
     * 按指定 Tool 编码执行 Tool。
     *
     * @param executionContext 运行时上下文
     * @param targetToolCode   目标 Tool 编码
     * @return 执行结果
     */
    public Optional<ToolExecutionResult> execute(ExecutionContext executionContext, String targetToolCode) {
        return execute(executionContext, targetToolCode, executionContext.userMessage(), Map.of());
    }

    /**
     * 按指定 Tool 编码执行 Tool，并显式传入当前 Tool 输入文本。
     *
     * @param executionContext 运行时上下文
     * @param targetToolCode   目标 Tool 编码
     * @param toolInputText    当前 Tool 输入文本
     * @return 执行结果
     */
    public Optional<ToolExecutionResult> execute(ExecutionContext executionContext,
                                                 String targetToolCode,
                                                 String toolInputText) {
        return execute(executionContext, targetToolCode, toolInputText, Map.of());
    }

    /**
     * 按指定 Tool 编码执行 Tool，并传入结构化参数。
     *
     * @param executionContext 运行时上下文
     * @param targetToolCode   目标 Tool 编码
     * @param toolInputText    当前 Tool 输入文本
     * @param arguments        当前 Tool 结构化参数
     * @return 执行结果
     */
    public Optional<ToolExecutionResult> execute(ExecutionContext executionContext,
                                                 String targetToolCode,
                                                 String toolInputText,
                                                 Map<String, Object> arguments) {
        List<RegistryAgentTool> candidates = toolLifecycleRoutingService.expand(executionContext,
                toolSelectionService.selectCandidates(executionContext, targetToolCode));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        ToolInvocationRequest rawRequest = new ToolInvocationRequest(toolInputText, arguments);
        ToolInvocationRequest request = new ToolInvocationRequest(
                toolInputText,
                ToolInvocationSupport.resolveArguments(candidates.get(0), rawRequest, objectMapper));
        CapabilityPolicyDecision capabilityDecision = capabilityPolicyEngine.decide(
                new CapabilityPolicyRequest(executionContext, candidates.get(0), request));
        String toolCallId = CapabilityApprovalContext.approvedToolCallId(
                capabilityDecision.approvalNodeId(), capabilityDecision.parameterHash());
        if (toolCallId == null) {
            toolCallId = toolRuntimeEventService.newToolCallId();
        }
        capabilityDecision = attachToolCallIdentity(capabilityDecision, toolCallId);
        capabilityPolicyRuntimeEventService.decided(
                executionContext, candidates.get(0), capabilityDecision, toolCallId);
        enforceCapabilityDecision(candidates.get(0), capabilityDecision);
        ToolSideEffectService.Lease sideEffectLease = toolSideEffectService.begin(executionContext, candidates.get(0), request);
        if (sideEffectLease.cached()) return Optional.of(sideEffectLease.cachedResult());
        ToolExecutionException lastException = null;
        boolean originalToolCallPending = true;
        for (int index = 0; index < candidates.size(); index++) {
            RegistryAgentTool candidate = candidates.get(index);
            if (!toolCircuitBreakerService.allow(executionContext, candidate)) {
                continue;
            }
            try {
                String candidateToolCallId = originalToolCallPending ? toolCallId : null;
                originalToolCallPending = false;
                ToolExecutionResult result = executeResolvedTool(
                        executionContext, candidate, request, candidateToolCallId);
                toolCircuitBreakerService.success(executionContext, candidate);
                toolSideEffectService.success(sideEffectLease, result);
                return Optional.of(result);
            } catch (ToolExecutionException exception) {
                toolCircuitBreakerService.failure(executionContext, candidate, exception);
                lastException = exception;
                ToolFallbackPolicyService.FallbackDecision decision = toolFallbackPolicyService.decide(
                        candidate,
                        exception,
                        index,
                        candidates.size()
                );
                if ("switch_candidate".equals(decision.action())) {
                    continue;
                }
                if ("replan".equals(decision.action())) {
                    ToolExecutionException replanException = new ToolExecutionException(
                            decision.reason() + "：" + exception.getMessage(),
                            exception.errorCode(),
                            exception.errorType(),
                            exception.retryable(),
                            exception.providerStatusCode(),
                            exception.metadata()
                    );
                    toolSideEffectService.uncertain(sideEffectLease, replanException);
                    throw replanException;
                }
                toolSideEffectService.uncertain(sideEffectLease, exception);
                throw exception;
            }
        }
        if (lastException != null) {
            toolSideEffectService.uncertain(sideEffectLease, lastException);
            throw lastException;
        }
        ToolExecutionException unavailable = new ToolExecutionException(
                "Tool 当前没有可接收流量的部署实例",
                "TOOL_NO_ROUTABLE_INSTANCE",
                "availability",
                true,
                null,
                Map.of("toolCode", targetToolCode)
        );
        toolSideEffectService.uncertain(sideEffectLease, unavailable);
        throw unavailable;
    }

    private CapabilityPolicyDecision attachToolCallIdentity(CapabilityPolicyDecision decision,
                                                             String toolCallId) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(decision.metadata());
        metadata.put("policyType", "capability");
        metadata.put("toolCallId", toolCallId);
        metadata.put("parameterHash", decision.parameterHash() == null ? "" : decision.parameterHash());
        return new CapabilityPolicyDecision(
                decision.action(), decision.policyCode(), decision.policyName(), decision.policyVersion(),
                decision.reason(), decision.matchedCapabilities(), decision.sideEffectLevel(),
                decision.parameterHash(), decision.targetHash(), decision.approvalNodeId(),
                decision.approvalGranted(), Map.copyOf(metadata));
    }

    private void enforceCapabilityDecision(RegistryAgentTool tool,
                                           CapabilityPolicyDecision decision) {
        if ("block".equals(decision.action())) {
            throw new PolicyViolationException(
                    "Tool「" + tool.toolName() + "」被 Capability Policy 阻断：" + decision.reason(),
                    decision.metadata());
        }
        if ("approval".equals(decision.action())) {
            throw new ApprovalRequiredException(
                    "Tool「" + tool.toolName() + "」需要能力审批：" + decision.reason(),
                    decision.approvalNodeId(),
                    "能力审批 - " + tool.toolName(),
                    null,
                    List.of(),
                    List.of(),
                    "capability_policy",
                    null,
                    decision.metadata()
            );
        }
    }

    public ToolExecutionResult executeResolvedTool(ExecutionContext executionContext,
                                                   RegistryAgentTool tool,
                                                   ToolInvocationRequest request) {
        return executeResolvedTool(executionContext, tool, request, null);
    }

    private ToolExecutionResult executeResolvedTool(ExecutionContext executionContext,
                                                    RegistryAgentTool tool,
                                                    ToolInvocationRequest request,
                                                    String preferredToolCallId) {
        Observation observation = Observation.start("agent.tool.call", observationRegistry)
                .lowCardinalityKeyValue("agent.tool.provider", safeValue(tool.providerType()))
                .lowCardinalityKeyValue("agent.tool.transport", safeValue(tool.transportType()))
                .highCardinalityKeyValue("agent.tool.code", tool.toolCode())
                .highCardinalityKeyValue("agent.task.trace_id", executionContext.taskContext().traceId());
        try (Observation.Scope ignored = observation.openScope()) {
            return doExecuteResolvedTool(executionContext, tool, request, preferredToolCallId);
        } catch (RuntimeException exception) {
            observation.error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }

    private ToolExecutionResult doExecuteResolvedTool(ExecutionContext executionContext,
                                                       RegistryAgentTool tool,
                                                       ToolInvocationRequest request,
                                                       String preferredToolCallId) {
        String resourceType = isMcp(tool) ? "mcp" : "tool";
        long startedAt = System.nanoTime();
        ToolRuntimeEventService.ToolCallEventContext toolCallContext =
                toolRuntimeEventService.requested(executionContext, tool, request, preferredToolCallId);
        ToolInvocationRequest resolvedRequest = request;
        try {
            costGovernanceService.assertAllowed(resourceType, tool.toolCode(), 0, 0, java.math.BigDecimal.ONE);
            Map<String, Object> resolvedArguments = ToolInvocationSupport.resolveArguments(
                    tool,
                    request,
                    objectMapper
            );
            ToolInvocationRequest invocationRequest = new ToolInvocationRequest(request.toolInputText(), resolvedArguments);
            resolvedRequest = invocationRequest;
            toolInvocationHooks.forEach(hook -> hook.beforeInvoke(executionContext, tool, invocationRequest));
            toolSchemaValidationService.validateInput(tool, resolvedArguments);
            ToolProviderAdapter toolProviderAdapter = toolProviderAdapters.stream()
                    .filter(adapter -> adapter.supports(tool.providerType()))
                    .findFirst()
                    .orElseThrow(() -> new ToolExecutionException(
                            "未找到 Tool Provider 适配器: " + tool.providerType(),
                            "TOOL_PROVIDER_ADAPTER_NOT_FOUND",
                            "configuration",
                            false,
                            null,
                            Map.of("toolCode", tool.toolCode(), "providerType", tool.providerType() == null ? "" : tool.providerType())
                    ));
            toolRuntimeEventService.started(toolCallContext);
            ToolExecutionResult result = toolProviderAdapter.execute(executionContext, tool, invocationRequest);
            toolSchemaValidationService.validateOutput(tool, result.outputSnapshot());
            toolInvocationHooks.forEach(hook -> hook.afterInvoke(executionContext, tool, invocationRequest, result));
            long durationMs = elapsedMs(startedAt);
            costGovernanceService.recordTool(executionContext, resourceType, tool.toolCode(), tool.providerType(),
                    durationMs, "success", Map.of("transportType", safeValue(tool.transportType())));
            toolRuntimeEventService.completed(toolCallContext, result, durationMs);
            return result;
        } catch (ToolExecutionException exception) {
            toolRuntimeEventService.failed(toolCallContext, exception, elapsedMs(startedAt));
            notifyError(executionContext, tool, resolvedRequest, exception);
            costGovernanceService.recordTool(executionContext, resourceType, tool.toolCode(), tool.providerType(),
                    elapsedMs(startedAt), "failed", Map.of("errorCode", safeValue(exception.errorCode())));
            throw exception;
        } catch (BusinessException exception) {
            ToolExecutionException wrapped = new ToolExecutionException(
                    exception.getMessage(),
                    "TOOL_EXECUTION_FAILED",
                    "execution",
                    false,
                    null,
                    Map.of("toolCode", tool.toolCode())
            );
            toolRuntimeEventService.failed(toolCallContext, wrapped, elapsedMs(startedAt));
            notifyError(executionContext, tool, resolvedRequest, wrapped);
            costGovernanceService.recordTool(executionContext, resourceType, tool.toolCode(), tool.providerType(),
                    elapsedMs(startedAt), "failed", Map.of("errorCode", "TOOL_EXECUTION_FAILED"));
            throw wrapped;
        } catch (RuntimeException exception) {
            toolRuntimeEventService.failed(toolCallContext, exception, elapsedMs(startedAt));
            throw exception;
        }
    }

    private boolean isMcp(RegistryAgentTool tool) {
        return "mcp".equalsIgnoreCase(tool.providerType())
                || (tool.transportType() != null && tool.transportType().toLowerCase().startsWith("mcp"));
    }

    private void notifyError(ExecutionContext executionContext,
                             RegistryAgentTool tool,
                             ToolInvocationRequest request,
                             ToolExecutionException exception) {
        toolInvocationHooks.forEach(hook -> hook.onError(executionContext, tool, request, exception));
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
