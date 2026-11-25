package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.ObservationDecisionService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyObservationPolicyAdapterTest {

    @Test
    void shouldDelegateExactObservationAndPreserveDecisionContract() {
        ObservationDecisionService observationDecisionService = mock(ObservationDecisionService.class);
        LegacyObservationPolicyAdapter adapter = new LegacyObservationPolicyAdapter(observationDecisionService);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ExecutionPlan executionPlan = mock(ExecutionPlan.class);
        ExecutionPlan.ExecutionPlanStep currentStep = mock(ExecutionPlan.ExecutionPlanStep.class);
        List<RuntimeStepResultContext> previousStepResults = List.of(
                new RuntimeStepResultContext(
                        1, "STEP_1", "tool", "查询", "完成", "result", "{}")
        );
        String outputSnapshot = "{\"resultType\":\"tool\"}";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "rule");
        metadata.put("remainingStepCount", 1);
        ObservationDecisionService.ObservationDecision legacyDecision =
                new ObservationDecisionService.ObservationDecision("replan", "需要重新规划", metadata);
        when(observationDecisionService.decide(
                executionContext, executionPlan, currentStep, previousStepResults, outputSnapshot
        )).thenReturn(legacyDecision);

        ObservationPolicy.Decision decision = adapter.evaluate(new ObservationPolicyRequest(
                executionContext,
                executionPlan,
                currentStep,
                previousStepResults,
                outputSnapshot
        ));

        assertThat(adapter.policyCode()).isEqualTo(LegacyObservationPolicyAdapter.POLICY_CODE);
        assertThat(decision.action()).isEqualTo("replan");
        assertThat(decision.reason()).isEqualTo("需要重新规划");
        assertThat(decision.metadata()).isSameAs(metadata);
        verify(observationDecisionService).decide(
                executionContext, executionPlan, currentStep, previousStepResults, outputSnapshot);
    }

    @Test
    void shouldNormalizeMissingPreviousResultsToEmptyList() {
        ExecutionPlan executionPlan = mock(ExecutionPlan.class);
        ExecutionPlan.ExecutionPlanStep currentStep = mock(ExecutionPlan.ExecutionPlanStep.class);

        ObservationPolicyRequest request = new ObservationPolicyRequest(
                null, executionPlan, currentStep, null, null);

        assertThat(request.previousStepResults()).isEmpty();
    }
}
