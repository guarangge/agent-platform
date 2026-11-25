package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionPlannerServiceTest {

    @Test
    void shouldSkipLlmPlannerForDirectReplyDecision() {
        RuntimeLlmGatewayService llmGatewayService = mock(RuntimeLlmGatewayService.class);
        ExecutionPlanService fallbackPlanService = mock(ExecutionPlanService.class);
        ExecutionPlannerService service = new ExecutionPlannerService(
                new ObjectMapper(), llmGatewayService, null, fallbackPlanService,
                null, null, null, null, new AgentExecutionPolicyResolver());
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = new ExecutionDecision("direct_reply", "直接回复", List.of(), List.of());
        ExecutionPlan expected = new ExecutionPlan(
                "PLAN_1", "direct_reply", "回答用户", "draft", List.of(), List.of(), List.of(), List.of());
        when(context.hasRunnableModel()).thenReturn(true);
        when(fallbackPlanService.buildPlan(context, decision)).thenReturn(expected);

        ExecutionPlan actual = service.buildPlan(context, decision);

        assertThat(actual).isSameAs(expected);
        verify(fallbackPlanService).buildPlan(context, decision);
        verifyNoInteractions(llmGatewayService);
    }

    @Test
    void shouldSkipLlmPlannerForSingleUserReplyWorkflow() {
        RuntimeLlmGatewayService llmGatewayService = mock(RuntimeLlmGatewayService.class);
        ExecutionPlanService fallbackPlanService = mock(ExecutionPlanService.class);
        ExecutionPlannerService service = service(llmGatewayService, fallbackPlanService);
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = new ExecutionDecision(
                "workflow", "命中流程", List.of(),
                List.of(new ExecutionDecision.SelectedWorkflow(9L, "通用回复流程")));
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.putObject("routing").put("outputMode", "user_reply");
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                9L, "GENERAL_REPLY", "通用回复流程", "agentic", "general",
                "published", "", metadata);
        ExecutionPlan expected = new ExecutionPlan(
                "PLAN_2", "workflow", "回答用户", "draft", List.of(), List.of(), List.of(), List.of());
        when(context.hasRunnableModel()).thenReturn(true);
        when(context.hasRunnableModel("planning")).thenReturn(true);
        when(context.plannerPromptContext()).thenReturn(new ExecutionContext.PromptContext(
                1L, "PLAN", "规划", "planner", "general", "拆解任务"));
        when(context.availableWorkflows()).thenReturn(List.of(workflow));
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                1L, "A", "Agent", "MODEL", "enabled", new ObjectMapper().createObjectNode()));
        when(fallbackPlanService.buildPlan(context, decision)).thenReturn(expected);

        ExecutionPlan actual = service.buildPlan(context, decision);

        assertThat(actual).isSameAs(expected);
        assertThat(service.resolvePlanningStrategy(context, decision, null).reason())
                .isEqualTo("single_user_reply_workflow_fast_path");
        assertThat(service.buildPlanningTraceMetadata(context, decision, null, List.of()))
                .extractingByKey("contextCompaction")
                .isEqualTo(java.util.Map.of("skipped", true, "reason", "single_user_reply_workflow_fast_path"));
        verifyNoInteractions(llmGatewayService);
    }

    private ExecutionPlannerService service(RuntimeLlmGatewayService llmGatewayService,
                                            ExecutionPlanService fallbackPlanService) {
        return new ExecutionPlannerService(
                new ObjectMapper(), llmGatewayService, null, fallbackPlanService,
                null, null, null, null, new AgentExecutionPolicyResolver());
    }
}
