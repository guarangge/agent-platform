package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 将 Registry Agent metadata 解析为有边界的 Decision/Replan 运行时策略。 */
@Service
public class AgentExecutionPolicyResolver {

    /** 解析 Agent 级执行策略，非法值回退安全默认值。 */
    public Policy resolve(ExecutionContext context) {
        JsonNode metadata = context == null || context.agentContext() == null
                ? null : context.agentContext().metadata();
        JsonNode planner = metadata == null ? null : metadata.path("plannerPolicy");
        JsonNode decision = metadata == null ? null : metadata.path("decisionPolicy");
        JsonNode execution = metadata == null ? null : metadata.path("executionPolicy");
        boolean autoReplanEnabled = bool(planner, "autoReplanEnabled", true);
        int maxReplanTimes = bounded(planner, "maxReplanTimes", 3, 0, 20);
        int circuitBreakerThreshold = bounded(planner, "replanCircuitBreakerThreshold", 3, 1, 20);
        int minimumQualityScore = bounded(planner, "minimumReplanQualityScore", 60, 0, 100);
        String failureAction = text(decision, "llmFailureAction", "rule_fallback");
        if (!List.of("rule_fallback", "direct_reply", "fail_fast").contains(failureAction)) {
            failureAction = "rule_fallback";
        }
        String decisionMode = text(decision, "mode", "llm_first");
        if (!List.of("llm_first", "rule_first", "rules_only").contains(decisionMode)) {
            decisionMode = "llm_first";
        }
        String planningMode = text(planner, "mode", "adaptive");
        if (!List.of("adaptive", "always_plan").contains(planningMode)) {
            planningMode = "adaptive";
        }
        boolean skipPlannerForDirectReply = bool(planner, "skipPlannerForDirectReply", true);
        boolean skipPlannerForSingleWorkflow = bool(planner, "skipPlannerForSingleWorkflow", true);
        int maxLlmCallsPerRun = bounded(execution, "maxLlmCallsPerRun", 6, 1, 50);
        return new Policy(autoReplanEnabled, maxReplanTimes, circuitBreakerThreshold,
                minimumQualityScore, failureAction, decisionMode, planningMode,
                skipPlannerForDirectReply, skipPlannerForSingleWorkflow, maxLlmCallsPerRun);
    }

    private boolean bool(JsonNode node, String field, boolean defaultValue) {
        return node == null || !node.path(field).isBoolean() ? defaultValue : node.path(field).asBoolean();
    }

    private int bounded(JsonNode node, String field, int defaultValue, int min, int max) {
        int value = node == null || !node.path(field).canConvertToInt() ? defaultValue : node.path(field).asInt();
        return Math.max(min, Math.min(max, value));
    }

    private String text(JsonNode node, String field, String defaultValue) {
        String value = node == null ? null : node.path(field).asText(null);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public record Policy(boolean autoReplanEnabled,
                         int maxReplanTimes,
                         int replanCircuitBreakerThreshold,
                         int minimumReplanQualityScore,
                         String decisionLlmFailureAction,
                         String decisionMode,
                         String planningMode,
                         boolean skipPlannerForDirectReply,
                         boolean skipPlannerForSingleWorkflow,
                         int maxLlmCallsPerRun) {
        public Map<String, Object> traceMetadata() {
            return Map.ofEntries(
                    Map.entry("autoReplanEnabled", autoReplanEnabled),
                    Map.entry("maxReplanTimes", maxReplanTimes),
                    Map.entry("replanCircuitBreakerThreshold", replanCircuitBreakerThreshold),
                    Map.entry("minimumReplanQualityScore", minimumReplanQualityScore),
                    Map.entry("decisionLlmFailureAction", decisionLlmFailureAction),
                    Map.entry("decisionMode", decisionMode),
                    Map.entry("planningMode", planningMode),
                    Map.entry("skipPlannerForDirectReply", skipPlannerForDirectReply),
                    Map.entry("skipPlannerForSingleWorkflow", skipPlannerForSingleWorkflow),
                    Map.entry("maxLlmCallsPerRun", maxLlmCallsPerRun)
            );
        }
    }
}
