package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStepHandlerRegistryTest {

    @Test
    void shouldRouteStepToRegisteredHandler() {
        ExecutionStepHandler handler = handler(Set.of(ExecutionStepType.JUDGE), "已执行");
        ExecutionStepHandlerRegistry registry = new ExecutionStepHandlerRegistry(List.of(handler));

        RuntimeStepExecutionOutcome result = registry.execute(request("judge"));

        assertThat(result.summary()).isEqualTo("已执行");
    }

    @Test
    void shouldRejectDuplicatedStepTypeRegistration() {
        ExecutionStepHandler first = handler(Set.of(ExecutionStepType.WAIT), "first");
        ExecutionStepHandler second = handler(Set.of(ExecutionStepType.WAIT), "second");

        assertThatThrownBy(() -> new ExecutionStepHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wait");
    }

    private ExecutionStepHandler handler(Set<ExecutionStepType> types, String summary) {
        return new ExecutionStepHandler() {
            @Override
            public Set<ExecutionStepType> supportedTypes() {
                return types;
            }

            @Override
            public RuntimeStepExecutionOutcome execute(RuntimeStepExecutionCommand request) {
                return new RuntimeStepExecutionOutcome("response", "{}", summary);
            }
        };
    }

    private RuntimeStepExecutionCommand request(String stepType) {
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1,
                "step-1",
                stepType,
                "测试步骤",
                "测试公共执行契约",
                List.of(),
                null,
                null,
                Map.of(),
                "执行完成",
                "fail_fast",
                false,
                "draft",
                "测试"
        );
        return new RuntimeStepExecutionCommand(
                null, null, null, step, List.of(), "", Map.of(), null, null, null);
    }
}
