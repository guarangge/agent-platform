package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanValidatorServiceTest {

    @Test
    void shouldRemoveReplyAfterWorkflowThatAlreadyProducesUserReply() throws Exception {
        StepDataContractService contractService = mock(StepDataContractService.class);
        PlanValidatorService service = new PlanValidatorService(contractService);
        ExecutionContext context = mock(ExecutionContext.class);
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                48L, "general_reply", "通用回复", "chat", "general", "enabled", "生成回复",
                new ObjectMapper().readTree("{\"routing\":{\"outputMode\":\"user_reply\"}}"));
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of(workflow));
        when(context.availableDelegates()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("测试问题");
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_1", "workflow", "回答问题", "draft", List.of(), List.of(), List.of(), List.of(
                step(1, "workflow_1", "workflow", 48L, List.of()),
                step(2, "reply_2", "reply", null, List.of("workflow_1"))
        ));

        ExecutionPlan normalized = service.validateAndNormalize(plan, context);

        assertThat(normalized.steps()).hasSize(1);
        assertThat(normalized.steps().get(0).stepType()).isEqualTo("workflow");
        assertThat(normalized.steps().get(0).failurePolicy()).isEqualTo("replan");
        assertThat(normalized.steps().get(0).replannable()).isTrue();
    }

    private ExecutionPlan.ExecutionPlanStep step(int stepNo,
                                                  String stepId,
                                                  String stepType,
                                                  Long workflowId,
                                                  List<String> dependencies) {
        return new ExecutionPlan.ExecutionPlanStep(
                stepNo, stepId, stepType, "步骤" + stepNo, "执行步骤", dependencies,
                null, workflowId, Map.of(), "返回结果", "fail_fast", true, "draft", "测试");
    }
}
