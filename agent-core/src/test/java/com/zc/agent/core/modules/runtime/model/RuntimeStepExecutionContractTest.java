package com.zc.agent.core.modules.runtime.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeStepExecutionContractTest {

    @Test
    void shouldPreserveStepExecutionInputsWithoutConversion() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = mock(ExecutionDecision.class);
        ExecutionPlan plan = mock(ExecutionPlan.class);
        ExecutionPlan.ExecutionPlanStep step = mock(ExecutionPlan.ExecutionPlanStep.class);
        List<RuntimeStepResultContext> previousResults = List.of(new RuntimeStepResultContext(
                1, "tool_1", "tool", "查询", "完成", "结果", "{}"));
        Map<String, Object> observation = Map.of("source", "user_input");

        RuntimeStepExecutionCommand command = new RuntimeStepExecutionCommand(
                context,
                decision,
                plan,
                step,
                previousResults,
                "执行输入",
                observation,
                "Bearer token",
                20L,
                30L
        );

        assertThat(command.executionContext()).isSameAs(context);
        assertThat(command.executionDecision()).isSameAs(decision);
        assertThat(command.executionPlan()).isSameAs(plan);
        assertThat(command.step()).isSameAs(step);
        assertThat(command.previousStepResults()).isSameAs(previousResults);
        assertThat(command.inputObservation()).isSameAs(observation);
        assertThat(command.inputText()).isEqualTo("执行输入");
        assertThat(command.authorization()).isEqualTo("Bearer token");
        assertThat(command.taskStepId()).isEqualTo(20L);
        assertThat(command.operatorId()).isEqualTo(30L);
    }

    @Test
    void shouldExposeStableStepExecutionOutcome() {
        RuntimeStepExecutionOutcome outcome = new RuntimeStepExecutionOutcome(
                "执行结果", "{\"resultType\":\"tool\"}", "执行摘要");

        assertThat(outcome.responseText()).isEqualTo("执行结果");
        assertThat(outcome.outputSnapshot()).isEqualTo("{\"resultType\":\"tool\"}");
        assertThat(outcome.summary()).isEqualTo("执行摘要");
    }
}
