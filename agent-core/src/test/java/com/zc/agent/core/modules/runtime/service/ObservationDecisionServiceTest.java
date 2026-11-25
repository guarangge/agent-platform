package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ObservationDecisionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseLlmToVerifyGoalAtTerminalStep() throws Exception {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ObservationDecisionService service = new ObservationDecisionService(objectMapper, gateway);
        ExecutionContext context = runnableContext();
        ExecutionPlan.ExecutionPlanStep step = terminalReplyStep(true);
        ExecutionPlan plan = plan(step);
        String snapshot = snapshot(false, "还需要继续调用数据库查询取得实际数据");
        when(gateway.chatCompletion(anyString(), anyString(), any()))
                .thenReturn("{\"action\":\"replan\",\"reason\":\"目标数据尚未取得\"}");
        when(gateway.sanitizeJsonText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ObservationDecisionService.ObservationDecision decision = service.decide(
                context, plan, step, previousResults(snapshot), snapshot);

        assertThat(decision.action()).isEqualTo("replan");
        assertThat(decision.metadata()).containsEntry("terminalGoalCheck", true);
        verify(gateway).chatCompletion(anyString(), anyString(), any());
    }

    @Test
    void shouldReplanTerminalStepWhenStructuredOutputRequestsItWithoutModel() throws Exception {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ObservationDecisionService service = new ObservationDecisionService(objectMapper, gateway);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.hasRunnableModel("decision")).thenReturn(false);
        ExecutionPlan.ExecutionPlanStep step = terminalReplyStep(true);
        ExecutionPlan plan = plan(step);
        String snapshot = snapshot(true, "需要补执行剩余动作");

        ObservationDecisionService.ObservationDecision decision = service.decide(
                context, plan, step, previousResults(snapshot), snapshot);

        assertThat(decision.action()).isEqualTo("replan");
        assertThat(decision.metadata()).containsEntry("terminalGoalCheck", true);
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldFinishTerminalStepWhenGoalIsComplete() throws Exception {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ObservationDecisionService service = new ObservationDecisionService(objectMapper, gateway);
        ExecutionContext context = runnableContext();
        ExecutionPlan.ExecutionPlanStep step = terminalReplyStep(true);
        ExecutionPlan plan = plan(step);
        String snapshot = snapshot(false, "已返回查询结果");
        when(gateway.chatCompletion(anyString(), anyString(), any()))
                .thenReturn("{\"action\":\"finish\",\"reason\":\"计划目标已经完成\"}");
        when(gateway.sanitizeJsonText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ObservationDecisionService.ObservationDecision decision = service.decide(
                context, plan, step, previousResults(snapshot), snapshot);

        assertThat(decision.action()).isEqualTo("finish");
        assertThat(decision.metadata()).containsEntry("terminalGoalCheck", true);
    }

    @Test
    void shouldFinishArtifactDeliveryWithoutCallingLlmAgain() throws Exception {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ObservationDecisionService service = new ObservationDecisionService(objectMapper, gateway);
        ExecutionContext context = runnableContext();
        ExecutionPlan.ExecutionPlanStep step = terminalReplyStep(true);
        ExecutionPlan plan = plan(step);
        String replySnapshot = snapshot(false, "已生成最终回复");
        String artifactSnapshot = """
                {"payload":{"artifacts":[{"path":"ai-learning-plan.pdf","size":2169,"contentBase64":"JVBERi0xLjQ="}]}}
                """;
        List<RuntimeStepResultContext> results = List.of(
                new RuntimeStepResultContext(
                        1, "tool_1", "tool", "Sandbox", "PDF 已生成", "generated", artifactSnapshot),
                new RuntimeStepResultContext(
                        2, "reply_1", "reply", "返回结果", "当前回复", "当前回复文本", replySnapshot)
        );

        ObservationDecisionService.ObservationDecision decision = service.decide(
                context, plan, step, results, replySnapshot);

        assertThat(decision.action()).isEqualTo("finish");
        assertThat(decision.metadata()).containsEntry("source", "artifact_delivery_rule");
        verifyNoInteractions(gateway);
    }

    private ExecutionContext runnableContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.hasRunnableModel("decision")).thenReturn(true);
        when(context.userMessage()).thenReturn("查询启用能力编码");
        when(context.conversationStatePayload()).thenReturn(Map.of());
        return context;
    }

    private ExecutionPlan plan(ExecutionPlan.ExecutionPlanStep step) {
        return new ExecutionPlan("PLAN_1", "hybrid", "返回数据库中的启用能力编码",
                "draft", List.of(), List.of(), List.of(), List.of(step));
    }

    private ExecutionPlan.ExecutionPlanStep terminalReplyStep(boolean replannable) {
        return new ExecutionPlan.ExecutionPlanStep(
                1, "reply_1", "reply", "返回结果", "返回实际查询结果", List.of(),
                null, null, Map.of(), "启用能力编码列表", "replan", replannable, "draft", "");
    }

    private List<RuntimeStepResultContext> previousResults(String snapshot) {
        return List.of(new RuntimeStepResultContext(
                1, "reply_1", "reply", "返回结果", "当前回复", "当前回复文本", snapshot));
    }

    private String snapshot(boolean needReplan, String observation) throws Exception {
        return objectMapper.writeValueAsString(new StandardStepOutputSnapshot(
                "reply", observation, observation, "当前回复", observation, 0.8d,
                needReplan, false, needReplan ? "继续完成剩余动作" : "", List.of(),
                Map.of(), Map.of(), Map.of()));
    }
}
