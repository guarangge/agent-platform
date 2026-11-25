package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanQualityServiceTest {

    @Test
    void shouldTreatUserReplyWorkflowAsTerminalOutput() {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.putObject("routing").put("outputMode", "user_reply");
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                48L, "GENERAL_REPLY", "通用回复", "agentic", "general", "published", "", metadata);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableWorkflows()).thenReturn(List.of(workflow));
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableDelegates()).thenReturn(List.of());
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1, "step_1", "workflow", "通用回复", "生成最终回复", List.of(), null, 48L,
                Map.of(), "完整用户回复", "fail_fast", false, "draft", "");
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_1", "workflow", "回答用户", "draft", List.of(), List.of(), List.of(), List.of(step));

        PlanQualityService.PlanQualityResult result = new PlanQualityService().evaluate(plan, context);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.issues()).isEmpty();
    }
}
