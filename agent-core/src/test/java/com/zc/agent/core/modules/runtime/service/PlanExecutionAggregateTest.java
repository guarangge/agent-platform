package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanExecutionAggregateTest {
    @Test
    void shouldExposeManualPauseWithoutTreatingItAsApproval(){
        ExecutionPlan plan=new ExecutionPlan("P","direct_reply","goal","draft",List.of(),List.of(),List.of(),List.of());
        var result=new RuntimeExecutionAggregate("paused",false,plan,"paused");
        assertThat(result.paused()).isTrue();assertThat(result.waitingApproval()).isFalse();assertThat(result.waitingEvent()).isFalse();
    }
}
