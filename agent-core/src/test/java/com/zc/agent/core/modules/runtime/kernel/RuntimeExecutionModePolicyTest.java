package com.zc.agent.core.modules.runtime.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.config.InteractiveLoopProperties;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopMetrics;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionMode;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeExecutionModePolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InteractiveLoopProperties properties = new InteractiveLoopProperties();
    private final RuntimeExecutionModePolicy policy = new RuntimeExecutionModePolicy(
            new InteractiveLoopRolloutPolicy(
                    properties, new InteractiveLoopMetrics(new SimpleMeterRegistry())));

    @Test
    void shouldKeepLegacyAgentOnWorkflowPlan() {
        RuntimeExecutionModePolicy.Selection selection = policy.select(kernelRequest(request(metadata(), null, List.of())));

        assertThat(selection.requestedMode()).isEqualTo(RuntimeExecutionMode.WORKFLOW_PLAN);
        assertThat(selection.effectiveMode()).isEqualTo(RuntimeExecutionMode.WORKFLOW_PLAN);
        assertThat(selection.reason()).isEqualTo("agent_default");
        assertThat(selection.fallback()).isFalse();
    }

    @Test
    void shouldFallbackWhileInteractiveLoopFlagIsOff() {
        ObjectNode metadata = metadata();
        metadata.putObject("executionPolicy")
                .put("defaultMode", "interactive_loop")
                .putArray("allowedModes")
                .add("workflow_plan")
                .add("interactive_loop");

        RuntimeExecutionModePolicy.Selection selection = policy.select(kernelRequest(request(metadata, null, List.of())));

        assertThat(selection.requestedMode()).isEqualTo(RuntimeExecutionMode.INTERACTIVE_LOOP);
        assertThat(selection.effectiveMode()).isEqualTo(RuntimeExecutionMode.WORKFLOW_PLAN);
        assertThat(selection.reason()).isEqualTo("interactive_loop_disabled");
        assertThat(selection.fallback()).isTrue();
    }

    @Test
    void shouldSelectInteractiveLoopWhenRolloutAllowsIt() {
        properties.setEnabled(true);
        properties.setAgentIds(List.of(1L));

        RuntimeExecutionModePolicy.Selection selection = policy.select(
                kernelRequest(request(interactiveMetadata(), null, List.of())));

        assertThat(selection.effectiveMode()).isEqualTo(RuntimeExecutionMode.INTERACTIVE_LOOP);
        assertThat(selection.reason()).isEqualTo("interactive_loop_agent_allowlisted");
        assertThat(selection.fallback()).isFalse();
    }

    @Test
    void shouldForceWorkflowPlanForWorkflowAndApproval() {
        ObjectNode metadata = interactiveMetadata();
        ExecutionDecision workflowDecision = new ExecutionDecision("workflow", "matched", List.of(),
                List.of(new ExecutionDecision.SelectedWorkflow(1L, "发布流程")));
        assertThat(policy.select(kernelRequest(request(metadata, workflowDecision, List.of()))).reason())
                .isEqualTo("workflow_selected");

        ExecutionPlan.ExecutionPlanStep approval = new ExecutionPlan.ExecutionPlanStep(
                1, "S1", "approval", "人工审批", "审批", List.of(), null, null,
                Map.of(), "approved", "fail_fast", false, "pending", "");
        assertThat(policy.select(kernelRequest(request(metadata, null, List.of(approval)))).reason())
                .isEqualTo("approval_required");
    }

    @Test
    void shouldRestoreWorkflowFallbackForHistoricalUnsafeMetadata() {
        ObjectNode metadata = metadata();
        metadata.putObject("executionPolicy")
                .put("defaultMode", "interactive_loop")
                .putArray("allowedModes")
                .add("interactive_loop");

        RuntimeExecutionModePolicy.Selection selection = policy.select(kernelRequest(request(metadata, null, List.of())));

        assertThat(selection.allowedModes()).contains(RuntimeExecutionMode.WORKFLOW_PLAN);
        assertThat(selection.effectiveMode()).isEqualTo(RuntimeExecutionMode.WORKFLOW_PLAN);
    }

    private ObjectNode metadata() {
        return objectMapper.createObjectNode();
    }

    private ObjectNode interactiveMetadata() {
        ObjectNode metadata = metadata();
        metadata.putObject("executionPolicy")
                .put("defaultMode", "interactive_loop")
                .putArray("allowedModes")
                .add("workflow_plan")
                .add("interactive_loop");
        return metadata;
    }

    private RuntimeExecutionRequest request(ObjectNode metadata,
                                            ExecutionDecision decision,
                                            List<ExecutionPlan.ExecutionPlanStep> steps) {
        ExecutionContext context = new ExecutionContext(
                null, null, null, null, null, null, List.of(), null,
                new ExecutionContext.AgentContext(1L, "agent", "Agent", "model", "enabled", metadata),
                null, null, null, null, null, Map.of(), null, null,
                List.of(), List.of(), List.of());
        ExecutionPlan plan = new ExecutionPlan("P1", "standard", "goal", "ready",
                List.of(), List.of(), List.of(), steps);
        return new RuntimeExecutionRequest(context, decision, plan, 1L, "TRACE_1", "{}",
                null, 1L, 1, 0, List.of(), null, null, null);
    }

    private AgentLoopExecutionRequest kernelRequest(RuntimeExecutionRequest request) {
        return new AgentLoopExecutionRequest(request, new RuntimeContextProvider.ResolvedRuntimeContext(
                request.executionContext(), 1L, 1L, 1L, 12L, 1L, 100L, 1L));
    }
}
