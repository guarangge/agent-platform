package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyEngine;
import com.zc.agent.core.modules.runtime.capability.CapabilityPolicyRuntimeEventService;
import com.zc.agent.core.modules.runtime.capability.CapabilityApprovalContext;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.service.ToolRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.PolicyViolationException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolRouterCapabilityPolicyTest {

    private final ExecutionContext executionContext = mock(ExecutionContext.class);
    private final RegistryAgentTool tool = tool();
    private final ToolProviderAdapter providerAdapter = mock(ToolProviderAdapter.class);
    private final ToolSideEffectService sideEffectService = mock(ToolSideEffectService.class);
    private final ToolRuntimeEventService toolEventService = mock(ToolRuntimeEventService.class);
    private final CapabilityPolicyEngine policyEngine = mock(CapabilityPolicyEngine.class);
    private final CapabilityPolicyRuntimeEventService policyEventService =
            mock(CapabilityPolicyRuntimeEventService.class);
    private final ToolCircuitBreakerService circuitBreakerService = mock(ToolCircuitBreakerService.class);
    private ToolRouterService routerService;

    @BeforeEach
    void setUp() {
        ToolSelectionService selectionService = mock(ToolSelectionService.class);
        ToolLifecycleRoutingService lifecycleRoutingService = mock(ToolLifecycleRoutingService.class);
        when(executionContext.taskContext()).thenReturn(
                new ExecutionContext.TaskContext(12L, "TASK_12", "TRACE_12"));
        when(selectionService.selectCandidates(executionContext, "PUBLISH_REPORT")).thenReturn(List.of(tool));
        when(lifecycleRoutingService.expand(executionContext, List.of(tool))).thenReturn(List.of(tool));
        when(toolEventService.newToolCallId()).thenReturn("TOOL_CALL_00000000000000000000");
        routerService = new ToolRouterService(
                List.of(providerAdapter),
                List.of(),
                mock(ToolSchemaValidationService.class),
                selectionService,
                lifecycleRoutingService,
                circuitBreakerService,
                mock(ToolFallbackPolicyService.class),
                new ObjectMapper(),
                ObservationRegistry.NOOP,
                mock(CostGovernanceService.class),
                sideEffectService,
                toolEventService,
                policyEngine,
                policyEventService
        );
    }

    @Test
    void shouldBlockBeforeSideEffectLeaseAndProviderInvocation() {
        CapabilityPolicyDecision decision = decision("block", null);
        when(policyEngine.decide(any())).thenReturn(decision);

        assertThatThrownBy(() -> routerService.execute(
                executionContext, "PUBLISH_REPORT", "发布报告", Map.of("path", "report.pdf")))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("Capability Policy 阻断");

        verify(policyEventService).decided(
                eq(executionContext), eq(tool),
                argThat(item -> "TOOL_CALL_00000000000000000000".equals(item.metadata().get("toolCallId"))),
                eq("TOOL_CALL_00000000000000000000"));
        ArgumentCaptor<CapabilityPolicyRequest> requestCaptor = ArgumentCaptor.forClass(CapabilityPolicyRequest.class);
        verify(policyEngine).decide(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().invocation().arguments())
                .containsEntry("targetPath", "report.pdf")
                .doesNotContainKey("path");
        verifyNoInteractions(sideEffectService, providerAdapter);
    }

    @Test
    void shouldRequestStableApprovalBeforeSideEffectLeaseAndProviderInvocation() {
        CapabilityPolicyDecision decision = decision(
                "approval", "capability:PUBLISH_REPORT:1234567890abcdef");
        when(policyEngine.decide(any())).thenReturn(decision);

        assertThatThrownBy(() -> routerService.execute(
                executionContext, "PUBLISH_REPORT", "发布报告", Map.of("path", "report.pdf")))
                .isInstanceOfSatisfying(ApprovalRequiredException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.approvalNodeId())
                            .isEqualTo("capability:PUBLISH_REPORT:1234567890abcdef");
                    org.assertj.core.api.Assertions.assertThat(exception.approvalSourceType())
                            .isEqualTo("capability_policy");
                    org.assertj.core.api.Assertions.assertThat(exception.approvalMetadata())
                            .containsEntry("toolCallId", "TOOL_CALL_00000000000000000000")
                            .containsEntry("parameterHash", "parameter-hash");
                });

        verify(policyEventService).decided(
                eq(executionContext), eq(tool), any(CapabilityPolicyDecision.class),
                eq("TOOL_CALL_00000000000000000000"));
        verifyNoInteractions(sideEffectService, providerAdapter);
    }

    @Test
    void shouldReuseApprovedToolCallIdWhenNodeAndParameterHashMatch() {
        String approvalNodeId = "capability:PUBLISH_REPORT:1234567890abcdef";
        String parameterHash = "a".repeat(64);
        CapabilityPolicyDecision approvedDecision = new CapabilityPolicyDecision(
                "allow", "capability.test", "测试能力策略", 1, "审批已通过",
                List.of("artifact.publish"), "irreversible", parameterHash, "target-hash",
                approvalNodeId, true, Map.of("policyDecision", "allow", "requestedAction", "approval"));
        when(policyEngine.decide(any())).thenReturn(approvedDecision);
        when(sideEffectService.begin(any(), eq(tool), any())).thenReturn(new ToolSideEffectService.Lease(null, false, null));
        when(circuitBreakerService.allow(executionContext, tool)).thenReturn(true);
        when(providerAdapter.supports("internal")).thenReturn(true);
        when(providerAdapter.execute(eq(executionContext), eq(tool), any())).thenReturn(
                new com.zc.agent.core.modules.tool.model.ToolExecutionResult(
                        "PUBLISH_REPORT", "发布报告", "完成", "发布完成", "{}"));

        try (CapabilityApprovalContext.Scope ignored = CapabilityApprovalContext.open(
                approvalNodeId, "approved", "TOOL_CALL_11111111111111111111", parameterHash)) {
            routerService.execute(executionContext, "PUBLISH_REPORT", "发布报告", Map.of("path", "report.pdf"));
        }

        verify(toolEventService).requested(
                eq(executionContext), eq(tool), any(), eq("TOOL_CALL_11111111111111111111"));
        verify(policyEventService).decided(
                eq(executionContext), eq(tool), any(CapabilityPolicyDecision.class),
                eq("TOOL_CALL_11111111111111111111"));
    }

    private CapabilityPolicyDecision decision(String action, String approvalNodeId) {
        return new CapabilityPolicyDecision(
                action,
                "capability.test",
                "测试能力策略",
                1,
                "测试策略决策",
                List.of("artifact.publish"),
                "irreversible",
                "parameter-hash",
                "target-hash",
                approvalNodeId,
                false,
                Map.of("policyDecision", action)
        );
    }

    private static RegistryAgentTool tool() {
        ObjectMapper objectMapper = new ObjectMapper();
        var argumentMapping = objectMapper.createObjectNode();
        argumentMapping.putObject("rename").put("path", "targetPath");
        return new RegistryAgentTool(
                1L, "PUBLISH_REPORT", "发布报告", "function", "general",
                List.of("artifact.publish"), "irreversible",
                null, null,
                "internal", "local", null, null, null, null, null,
                null, null, null, null, 30, 0, null, null, null, argumentMapping, null,
                "high", 30, "enabled", "", null
        );
    }
}
