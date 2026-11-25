package com.zc.agent.core.modules.runtime.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlanOutcomeFeedbackServiceTest {

    @Test
    void shouldSeparateSuccessfulAndFailedPlanOutcomes() {
        PlanOutcomeFeedbackService service = new PlanOutcomeFeedbackService();
        List<ExecutionPlannerService.PlanTemplateHint> hints = List.of(
                new ExecutionPlannerService.PlanTemplateHint("P1", 1, "agentic", "goal", 90,
                        "ok", "success", "", List.of("1:tool:search")),
                new ExecutionPlannerService.PlanTemplateHint("P2", 1, "agentic", "goal", 70,
                        "failed", "failed", "tool timeout", List.of("1:tool:search"))
        );

        Map<String, Object> feedback = service.summarize(hints);

        assertEquals(2, feedback.get("sampleCount"));
        assertEquals(1, ((List<?>) feedback.get("successfulPatterns")).size());
        assertEquals(1, ((List<?>) feedback.get("failedPatterns")).size());
        assertFalse(((List<?>) feedback.get("recommendations")).isEmpty());
    }
}
