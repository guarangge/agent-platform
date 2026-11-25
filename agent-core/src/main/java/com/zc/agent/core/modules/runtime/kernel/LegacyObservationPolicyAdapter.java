package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.service.ObservationDecisionService;
import org.springframework.stereotype.Service;

/** 将现有规则 / LLM observation 决策服务适配为 ObservationPolicy。 */
@Service
public class LegacyObservationPolicyAdapter implements ObservationPolicy {

    public static final String POLICY_CODE = "legacy_observation_policy_v1";

    private final ObservationDecisionService observationDecisionService;

    public LegacyObservationPolicyAdapter(ObservationDecisionService observationDecisionService) {
        this.observationDecisionService = observationDecisionService;
    }

    @Override
    public String policyCode() {
        return POLICY_CODE;
    }

    @Override
    public Decision evaluate(ObservationPolicyRequest request) {
        ObservationDecisionService.ObservationDecision decision = observationDecisionService.decide(
                request.executionContext(),
                request.executionPlan(),
                request.currentStep(),
                request.previousStepResults(),
                request.currentOutputSnapshot()
        );
        return new Decision(decision.action(), decision.reason(), decision.metadata());
    }
}
