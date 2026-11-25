package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionResponseAssemblerServiceTest {

    @Test
    void shouldReuseTerminalReplyInsteadOfCallingLlmSummaryAgain() {
        ExecutionResponseAssemblerService service = new ExecutionResponseAssemblerService(
                null, null, null, null, null, null, new ObjectMapper());
        ExecutionDecision decision = new ExecutionDecision("workflow", "执行工作流", List.of(), List.of());
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_1", "workflow", "生成方案", "draft", List.of(), List.of(), List.of(), List.of());
        List<RuntimeStepResultContext> results = List.of(
                new RuntimeStepResultContext(
                        1, "workflow_1", "workflow", "收集信息", "已收集", "中间结果", "{}"),
                new RuntimeStepResultContext(
                        2, "reply_2", "reply", "输出方案", "已生成", "可直接返回的最终回复", "{}")
        );

        String response = service.assemblePlanResponse(
                null, decision, plan, List.of("步骤 1", "步骤 2"), results);

        assertThat(response).isEqualTo("可直接返回的最终回复");
    }

    @Test
    void shouldReturnRawUserReplyWorkflowResultWithoutDebugWrapper() throws Exception {
        ExecutionResponseAssemblerService service = new ExecutionResponseAssemblerService(
                null, null, null, null, null, null, new ObjectMapper());
        ExecutionContext context = mock(ExecutionContext.class);
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                48L, "general_reply", "通用回复", "chat", "general", "enabled", "生成回复",
                new ObjectMapper().readTree("{\"routing\":{\"outputMode\":\"user_reply\"}}"));
        when(context.availableWorkflows()).thenReturn(List.of(workflow));
        ExecutionDecision decision = new ExecutionDecision("workflow", "执行工作流", List.of(), List.of());
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1, "workflow_1", "workflow", "通用回复", "回答问题", List.of(),
                null, 48L, java.util.Map.of(), "回复", "fail_fast", true, "draft", "测试");
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_2", "workflow", "回答用户", "draft", List.of(), List.of(), List.of(), List.of(step));
        var result = new RuntimeStepResultContext(
                1, "workflow_1", "workflow", "通用回复", "已完成", "测试完成。", "{}");

        String response = service.assemblePlanResponse(
                context, decision, plan, List.of("已执行 Workflow：测试完成。"), List.of(result));

        assertThat(response).isEqualTo("测试完成。");
    }
}
