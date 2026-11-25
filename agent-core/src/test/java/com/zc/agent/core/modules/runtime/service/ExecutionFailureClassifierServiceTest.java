package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionFailureClassifierServiceTest {
    private final ExecutionFailureClassifierService service = new ExecutionFailureClassifierService();

    @Test
    void shouldClassifyRetryableToolFailure() {
        var result = service.classify(step(true), new ToolExecutionException(
                "timeout", "TOOL_TIMEOUT", "provider", true, 504, Map.of()));
        assertThat(result.disposition()).isEqualTo("retryable");
        assertThat(result.replannable()).isTrue();
    }

    @Test
    void shouldRequireApprovalForUncertainSideEffect() {
        var result = service.classify(step(true), new ToolExecutionException(
                "uncertain", "TOOL_SIDE_EFFECT_UNCERTAIN", "side_effect", false, null, Map.of()));
        assertThat(result.disposition()).isEqualTo("approval_required");
        assertThat(result.replannable()).isFalse();
    }

    private ExecutionPlan.ExecutionPlanStep step(boolean replannable) {
        return new ExecutionPlan.ExecutionPlanStep(1,"s1","tool","tool","tool",List.of(),"T",null,
                Map.of(),"result","replan",replannable,"draft","");
    }
}
