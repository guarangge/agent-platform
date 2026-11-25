package com.zc.agent.core.modules.runtime.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStepTypeTest {

    @Test
    void shouldExposeDifferentPlanAndWorkflowBoundaries() {
        assertThat(ExecutionStepType.planCodes()).contains("tool", "workflow", "reply");
        assertThat(ExecutionStepType.planCodes()).doesNotContain("start", "end");
        assertThat(ExecutionStepType.requireWorkflowType("start")).isEqualTo(ExecutionStepType.START);
        assertThatThrownBy(() -> ExecutionStepType.requireWorkflowType("workflow"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProvideSharedExecutionSemantics() {
        assertThat(ExecutionStepType.requirePlanType("reply").terminal()).isTrue();
        assertThat(ExecutionStepType.requirePlanType("tool").factProducing()).isTrue();
        assertThat(ExecutionStepType.requirePlanType("parallel").traceType()).isEqualTo("tool");
    }
}
