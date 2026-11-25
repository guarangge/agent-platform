package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时 observation 决策服务。
 *
 * 每一步执行完成后，基于当前 observation 决定下一跳动作：
 * 1. continue：继续执行原计划
 * 2. replan：触发后续自动重规划
 * 3. finish：提前收束当前执行链
 */
@Service
public class ObservationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ObservationDecisionService.class);

    private final ObjectMapper objectMapper;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;

    public ObservationDecisionService(ObjectMapper objectMapper,
                                      RuntimeLlmGatewayService runtimeLlmGatewayService) {
        this.objectMapper = objectMapper;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
    }

    public ObservationDecision decide(ExecutionContext executionContext,
                                      ExecutionPlan executionPlan,
                                      ExecutionPlan.ExecutionPlanStep currentStep,
                                      List<RuntimeStepResultContext> previousStepResults,
                                      String currentOutputSnapshot) {
        StandardStepOutputSnapshot snapshot = parseStandardSnapshot(currentOutputSnapshot);
        int remainingStepCount = (int) executionPlan.steps().stream()
                .filter(item -> item.stepNo() > currentStep.stepNo())
                .count();
        if ("clarify".equals(currentStep.stepType())) {
            return new ObservationDecision("finish", "clarify 步骤已向用户发起补问，当前执行先结束等待输入", Map.of(
                    "remainingStepCount", remainingStepCount,
                    "source", "rule",
                    "stepType", "clarify"
            ));
        }
        if (snapshot == null) {
            return new ObservationDecision("continue", "当前步骤未产出标准 observation，继续执行后续计划", Map.of(
                    "remainingStepCount", remainingStepCount,
                    "source", "observation_fallback"
            ));
        }
        ObservationDecision ruleDecision = decideByRule(currentStep, snapshot, remainingStepCount);
        if ("reply".equals(currentStep.stepType()) && remainingStepCount == 0
                && containsArtifact(previousStepResults)) {
            return new ObservationDecision("finish", "前序步骤已生成可下载产物，结束执行并交付文件", Map.of(
                    "remainingStepCount", 0,
                    "source", "artifact_delivery_rule",
                    "terminalGoalCheck", true
            ));
        }
        if (!executionContext.hasRunnableModel("decision")) {
            return ruleDecision;
        }
        try {
            ObservationDecision llmDecision = decideByLlm(executionContext, executionPlan, currentStep, previousStepResults, snapshot, remainingStepCount);
            if (llmDecision != null) {
                return llmDecision;
            }
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            log.warn("observation 决策失败，回退规则判断：{}", exception.getMessage());
        }
        return ruleDecision;
    }

    private boolean containsArtifact(List<RuntimeStepResultContext> stepResults) {
        if (stepResults == null) {
            return false;
        }
        for (RuntimeStepResultContext stepResult : stepResults) {
            try {
                JsonNode artifacts = objectMapper.readTree(stepResult.outputSnapshot()).path("payload").path("artifacts");
                if (artifacts.isArray() && !artifacts.isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
                // 非 JSON 或非产物步骤不影响 Observation 的常规判断。
            }
        }
        return false;
    }

    private ObservationDecision decideByRule(ExecutionPlan.ExecutionPlanStep currentStep,
                                             StandardStepOutputSnapshot snapshot,
                                             int remainingStepCount) {
        if (remainingStepCount == 0) {
            if (Boolean.TRUE.equals(currentStep.replannable()) && Boolean.TRUE.equals(snapshot.needReplan())) {
                return new ObservationDecision("replan", firstNonBlank(
                        snapshot.nextActionHint(),
                        snapshot.observation(),
                        snapshot.summary(),
                        "最后一步尚未完成计划目标，需要重新规划"
                ), Map.of(
                        "remainingStepCount", 0,
                        "source", "rule",
                        "terminalGoalCheck", true,
                        "needReplan", true
                ));
            }
            return new ObservationDecision("finish", "当前已是最后一步，结束执行", Map.of(
                    "remainingStepCount", 0,
                    "source", "rule",
                    "terminalGoalCheck", false
            ));
        }
        Map<String, Object> inputs = currentStep.inputs() == null ? Map.of() : currentStep.inputs();
        Double confidenceBelow = numberValue(inputs.get("confidenceBelow"));
        boolean lowConfidence = confidenceBelow != null
                && snapshot.confidence() != null
                && snapshot.confidence() < confidenceBelow;
        boolean replanOnNeedMoreInfo = booleanValue(inputs.get("replanOnNeedMoreInfo"));
        boolean reflectionDefault = "reflection".equals(currentStep.stepType());
        boolean postStepReplan = reflectionDefault || booleanValue(inputs.get("postStepReplan"));
        if (postStepReplan && (Boolean.TRUE.equals(snapshot.needReplan())
                || lowConfidence
                || (replanOnNeedMoreInfo && Boolean.TRUE.equals(snapshot.needMoreInfo())))) {
            return new ObservationDecision("replan", firstNonBlank(
                    snapshot.nextActionHint(),
                    snapshot.observation(),
                    snapshot.summary(),
                    "当前 observation 表明应重新调整后续执行路径"
            ), Map.of(
                    "remainingStepCount", remainingStepCount,
                    "source", "rule",
                    "needReplan", Boolean.TRUE.equals(snapshot.needReplan()),
                    "needMoreInfo", Boolean.TRUE.equals(snapshot.needMoreInfo()),
                    "confidence", snapshot.confidence() == null ? "" : snapshot.confidence()
            ));
        }
        return new ObservationDecision("continue", firstNonBlank(
                snapshot.nextActionHint(),
                snapshot.observation(),
                "继续执行后续计划"
        ), Map.of(
                "remainingStepCount", remainingStepCount,
                "source", "rule"
        ));
    }

    private ObservationDecision decideByLlm(ExecutionContext executionContext,
                                            ExecutionPlan executionPlan,
                                            ExecutionPlan.ExecutionPlanStep currentStep,
                                            List<RuntimeStepResultContext> previousStepResults,
                                            StandardStepOutputSnapshot snapshot,
                                            int remainingStepCount) throws Exception {
        String responseText = runtimeLlmGatewayService.chatCompletion(
                """
                你是 Agent 运行时 observation 决策器。
                你只负责判断当前 observation 后，应该继续执行、结束执行，还是触发重规划。
                必须返回严格 JSON，不要 Markdown，不要解释。
                输出格式：
                {
                  "action": "continue|replan|finish",
                  "reason": "30字以内中文原因"
                }
                约束：
                1. 如果 remainingStepCount > 0，除非 observation 明确表明后续路径失效，否则不要轻易 finish。
                2. 如果 observation 表明信息不足、置信度低或路径偏差，应优先返回 replan。
                3. 当 remainingStepCount = 0 时，必须对照 planGoal 判断目标是否实质完成，不能因为已经执行到最后一步就直接 finish。
                4. 如果最终回复仍表示需要继续调用 Tool、执行查询、获取数据、完成操作或补做其他可由 Agent 自主完成的动作，返回 replan。
                5. 只有用户必须补充信息且当前输出已经明确向用户澄清时，才允许 finish 等待下一轮输入。
                6. 不要编造新的业务事实。
                """,
                objectMapper.writeValueAsString(buildDecisionPayload(
                        executionContext, executionPlan, currentStep, previousStepResults, snapshot, remainingStepCount)),
                executionContext.modelForStage("decision")
        );
        JsonNode root = objectMapper.readTree(runtimeLlmGatewayService.sanitizeJsonText(responseText));
        String action = root.path("action").asText("continue").trim().toLowerCase();
        if (!"continue".equals(action) && !"replan".equals(action) && !"finish".equals(action)) {
            action = "continue";
        }
        if ("finish".equals(action) && remainingStepCount > 0 && !"reply".equals(currentStep.stepType())) {
            action = "continue";
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("remainingStepCount", remainingStepCount);
        metadata.put("source", "llm");
        metadata.put("confidence", snapshot.confidence());
        metadata.put("needReplan", snapshot.needReplan());
        metadata.put("needMoreInfo", snapshot.needMoreInfo());
        metadata.put("terminalGoalCheck", remainingStepCount == 0);
        return new ObservationDecision(
                action,
                firstNonBlank(root.path("reason").asText(null), snapshot.nextActionHint(), snapshot.observation(), "继续执行后续计划"),
                metadata
        );
    }

    private Map<String, Object> buildDecisionPayload(
            ExecutionContext executionContext,
            ExecutionPlan executionPlan,
            ExecutionPlan.ExecutionPlanStep currentStep,
            List<RuntimeStepResultContext> previousStepResults,
            StandardStepOutputSnapshot snapshot,
            int remainingStepCount) {
        LinkedHashMap<String, Object> currentStepPayload = new LinkedHashMap<>();
        currentStepPayload.put("stepNo", currentStep.stepNo());
        currentStepPayload.put("stepId", safeText(currentStep.stepId()));
        currentStepPayload.put("stepType", safeText(currentStep.stepType()));
        currentStepPayload.put("stepName", safeText(currentStep.stepName()));
        currentStepPayload.put("objective", safeText(currentStep.objective()));

        List<Map<String, Object>> previousSteps = previousStepResults.stream().map(item -> {
            LinkedHashMap<String, Object> stepPayload = new LinkedHashMap<>();
            stepPayload.put("stepNo", item.stepNo());
            stepPayload.put("stepId", safeText(item.stepId()));
            stepPayload.put("stepType", safeText(item.stepType()));
            stepPayload.put("stepName", safeText(item.stepName()));
            stepPayload.put("summary", safeText(item.summary()));
            stepPayload.put("responseText", safeText(item.responseText()));
            return Map.<String, Object>copyOf(stepPayload);
        }).toList();

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("userMessage", safeText(executionContext.userMessage()));
        payload.put("conversationState", executionContext.conversationStatePayload() == null
                ? Map.of() : executionContext.conversationStatePayload());
        payload.put("conversationStateText", safeText(executionContext.conversationStatePromptText()));
        payload.put("conversationMemory", safeText(executionContext.conversationMemoryText()));
        payload.put("planGoal", safeText(executionPlan.goal()));
        payload.put("currentStep", currentStepPayload);
        payload.put("remainingStepCount", remainingStepCount);
        payload.put("currentObservation", snapshot);
        payload.put("previousSteps", previousSteps);
        return payload;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private StandardStepOutputSnapshot parseStandardSnapshot(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(outputSnapshot);
            if (!rootNode.isObject() || rootNode.path("resultType").asText("").isBlank()) {
                return null;
            }
            return objectMapper.treeToValue(rootNode, StandardStepOutputSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private Double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    public record ObservationDecision(
            String action,
            String reason,
            Map<String, Object> metadata
    ) {
    }
}
