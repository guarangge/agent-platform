package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 condition 步骤服务。
 * 负责执行条件判断，以及根据前序条件结果决定后续步骤是否应继续执行。
 */
@Service
public class ConditionStepService {

    private static final Logger log = LoggerFactory.getLogger(ConditionStepService.class);

    private final ObjectMapper objectMapper;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;

    public ConditionStepService(ObjectMapper objectMapper,
                                RuntimeLlmGatewayService runtimeLlmGatewayService) {
        this.objectMapper = objectMapper;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
    }

    /**
     * 判断当前步骤是否应该执行。
     *
     * @param step                当前步骤
     * @param previousStepResults 前序步骤结果
     * @return true 表示执行，false 表示跳过
     */
    public boolean shouldExecute(ExecutionPlan.ExecutionPlanStep step,
                                 List<RuntimeStepResultContext> previousStepResults) {
        if (step.inputs() == null) {
            return true;
        }
        Object runWhenObject = step.inputs().get("runWhen");
        if (runWhenObject == null) {
            return true;
        }
        List<Map<String, Object>> rules = normalizeRunWhenRules(runWhenObject);
        if (rules.isEmpty()) {
            return true;
        }
        for (Map<String, Object> rule : rules) {
            String conditionStepId = stringValue(rule.get("conditionStepId"));
            if (conditionStepId.isBlank()) {
                throw new BusinessException("runWhen.conditionStepId 不能为空");
            }
            boolean expectedResult = booleanValue(rule.get("expectedResult"));
            RuntimeStepResultContext matchedContext = previousStepResults.stream()
                    .filter(item -> conditionStepId.equals(item.stepId()))
                    .findFirst()
                    .orElse(null);
            if (matchedContext == null) {
                return false;
            }
            boolean actualResult = extractMatchedResult(matchedContext.outputSnapshot());
            if (actualResult != expectedResult) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 condition 步骤。
     *
     * @param step              当前步骤
     * @param executionContext  执行上下文
     * @param conditionInputText 条件输入文本
     * @return 条件执行结果
     */
    public ConditionExecutionResult execute(ExecutionPlan.ExecutionPlanStep step,
                                            ExecutionContext executionContext,
                                            String conditionInputText) {
        Map<String, Object> inputs = step.inputs() == null ? Map.of() : step.inputs();
        ConditionMatchResult conditionMatchResult = evaluateCondition(inputs, executionContext, conditionInputText);
        boolean matched = conditionMatchResult.matched();
        String summary = matched ? "条件判断结果为 true" : "条件判断结果为 false";
        String responseText = "步骤" + step.stepNo() + "「" + step.stepName() + "」完成条件判断：" + matched;
        Map<String, Object> outputSnapshot = new LinkedHashMap<>();
        outputSnapshot.put("resultType", "condition");
        outputSnapshot.put("assistantMessage", responseText);
        outputSnapshot.put("responseText", responseText);
        outputSnapshot.put("summary", summary);
        outputSnapshot.put("observation", conditionMatchResult.reason());
        outputSnapshot.put("confidence", 1.0d);
        outputSnapshot.put("needReplan", false);
        outputSnapshot.put("needMoreInfo", false);
        outputSnapshot.put("matched", matched);
        outputSnapshot.put("reason", conditionMatchResult.reason());
        outputSnapshot.put("conditionInputText", conditionInputText);
        outputSnapshot.put("conditionConfig", inputs);
        return new ConditionExecutionResult(responseText, toJsonText(outputSnapshot), summary, matched);
    }

    public boolean evaluateWorkflowCondition(JsonNode node,
                                            ExecutionContext executionContext,
                                            String conditionInputText) {
        return evaluateCondition(jsonNodeToMap(node), executionContext, conditionInputText).matched();
    }

    private ConditionMatchResult evaluateCondition(Map<String, Object> inputs,
                                                   ExecutionContext executionContext,
                                                   String conditionInputText) {
        String mode = stringValue(inputs.get("mode")).toLowerCase(Locale.ROOT);
        boolean llmMode = "llm".equals(mode)
                || !stringValue(inputs.get("llmCondition")).isBlank()
                || !stringValue(inputs.get("semanticRule")).isBlank()
                || isSemanticExpression(inputs.get("expression"));
        if (llmMode) {
            return evaluateByLlm(inputs, executionContext, conditionInputText);
        }
        List<String> keywords = normalizeStringList(inputs.get("keywords"));
        String keyword = stringValue(inputs.get("keyword"));
        if (!keyword.isBlank()) {
            keywords.add(keyword);
        }
        if (!keywords.isEmpty()) {
            boolean matched = keywords.stream().anyMatch(conditionInputText::contains);
            return new ConditionMatchResult(matched, "关键词规则判断");
        }
        String availableToolCode = stringValue(inputs.get("availableToolCode"));
        if (!availableToolCode.isBlank()) {
            boolean matched = executionContext.availableTools().stream()
                    .anyMatch(item -> availableToolCode.equals(item.toolCode()));
            return new ConditionMatchResult(matched, "可用 Tool 规则判断");
        }
        String expression = stringValue(inputs.get("expression")).toLowerCase(Locale.ROOT);
        if ("always_true".equals(expression)) {
            return new ConditionMatchResult(true, "固定 true 表达式");
        }
        if ("always_false".equals(expression)) {
            return new ConditionMatchResult(false, "固定 false 表达式");
        }
        throw new BusinessException("condition 步骤缺少可识别的判断配置");
    }

    private ConditionMatchResult evaluateByLlm(Map<String, Object> inputs,
                                               ExecutionContext executionContext,
                                               String conditionInputText) {
        if (!executionContext.hasRunnableModel("decision")) {
            throw new BusinessException("condition 语义判断需要可用模型");
        }
        String semanticRule = stringValue(inputs.get("llmCondition"));
        if (semanticRule.isBlank()) {
            semanticRule = stringValue(inputs.get("semanticRule"));
        }
        if (semanticRule.isBlank()) {
            semanticRule = stringValue(inputs.get("expression"));
        }
        if (semanticRule.isBlank()) {
            throw new BusinessException("condition LLM 判断缺少 llmCondition / semanticRule / expression");
        }
        try {
            String responseText = runtimeLlmGatewayService.chatCompletion(
                    """
                    你是 Agent 条件判断器。
                    你只负责根据给定规则判断条件是否命中。
                    必须返回严格 JSON，不要 Markdown，不要解释。
                    输出格式：
                    {
                      "matched": true,
                      "reason": "20字以内中文原因"
                    }
                    """,
                    objectMapper.writeValueAsString(Map.of(
                            "rule", semanticRule,
                            "inputText", conditionInputText,
                            "userMessage", executionContext.userMessage(),
                            "conversationState", executionContext.conversationStatePayload(),
                            "conversationStateText", safeText(executionContext.conversationStatePromptText()),
                            "conversationMemory", safeText(executionContext.conversationMemoryText()),
                            "availableToolCodes", executionContext.availableTools().stream().map(item -> item.toolCode()).toList(),
                            "userMemory", safeText(executionContext.userMemoryText()),
                            "recentTaskMemory", safeText(executionContext.recentTaskMemoryText())
                    )),
                    executionContext.modelForStage("decision")
            );
            JsonNode node = objectMapper.readTree(runtimeLlmGatewayService.sanitizeJsonText(responseText));
            return new ConditionMatchResult(
                    node.path("matched").asBoolean(false),
                    stringValue(node.path("reason").asText(null))
            );
        } catch (Exception exception) {
            log.warn("condition LLM 判断失败，message={}", exception.getMessage());
            throw new BusinessException("condition 语义判断失败: " + exception.getMessage());
        }
    }

    private List<Map<String, Object>> normalizeRunWhenRules(Object runWhenObject) {
        if (runWhenObject instanceof List<?> list) {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rules.add(castMap(map));
                }
            }
            return rules;
        }
        if (runWhenObject instanceof Map<?, ?> map) {
            return List.of(castMap(map));
        }
        return List.of();
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            String text = stringValue(item);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private boolean extractMatchedResult(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return false;
        }
        try {
            return objectMapper.readTree(outputSnapshot).path("matched").asBoolean(false);
        } catch (Exception exception) {
            return false;
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("condition 执行快照序列化失败");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isSemanticExpression(Object expression) {
        String normalizedExpression = stringValue(expression).toLowerCase(Locale.ROOT);
        return !normalizedExpression.isBlank()
                && !"always_true".equals(normalizedExpression)
                && !"always_false".equals(normalizedExpression);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private record ConditionMatchResult(
            boolean matched,
            String reason
    ) {
    }

    /**
     * condition 执行结果。
     */
    public record ConditionExecutionResult(
            String responseText,
            String outputSnapshot,
            String summary,
            boolean matched
    ) {
    }
}
