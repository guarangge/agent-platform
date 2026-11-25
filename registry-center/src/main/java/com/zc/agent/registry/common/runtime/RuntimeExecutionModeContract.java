package com.zc.agent.registry.common.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** Registry 侧 Agent 双执行模式配置契约。 */
public final class RuntimeExecutionModeContract {

    public static final String WORKFLOW_PLAN = "workflow_plan";
    public static final String INTERACTIVE_LOOP = "interactive_loop";
    public static final Set<String> MODES = Set.of(WORKFLOW_PLAN, INTERACTIVE_LOOP);

    private RuntimeExecutionModeContract() {
    }

    /**
     * 校验 metadata.executionPolicy 中的执行模式声明。
     * 未声明时继续使用 workflow_plan，保证历史 Agent 兼容。
     */
    public static void validate(JsonNode metadata) {
        JsonNode policy = metadata == null ? null : metadata.path("executionPolicy");
        if (policy == null || policy.isMissingNode()) {
            return;
        }
        if (!policy.isObject()) {
            throw new BusinessException("metadata.executionPolicy 必须为 JSON 对象");
        }
        JsonNode defaultModeNode = policy.get("defaultMode");
        JsonNode allowedModesNode = policy.get("allowedModes");
        validateInteractiveLoopPolicy(policy.path("interactiveLoop"), policy.path("maxLlmCallsPerRun"));
        if (defaultModeNode == null && allowedModesNode == null) {
            return;
        }
        if (defaultModeNode == null || !defaultModeNode.isTextual()) {
            throw new BusinessException("executionPolicy.defaultMode 必须为执行模式字符串");
        }
        String defaultMode = defaultModeNode.asText().trim();
        validateMode(defaultMode, "默认执行模式");
        if (allowedModesNode == null || !allowedModesNode.isArray() || allowedModesNode.isEmpty()) {
            throw new BusinessException("executionPolicy.allowedModes 必须为非空数组");
        }
        LinkedHashSet<String> allowedModes = new LinkedHashSet<>();
        for (JsonNode modeNode : allowedModesNode) {
            if (!modeNode.isTextual()) {
                throw new BusinessException("executionPolicy.allowedModes 仅支持字符串");
            }
            String mode = modeNode.asText().trim();
            validateMode(mode, "允许执行模式");
            if (!allowedModes.add(mode)) {
                throw new BusinessException("允许执行模式重复: " + mode);
            }
        }
        if (!allowedModes.contains(WORKFLOW_PLAN)) {
            throw new BusinessException("允许执行模式必须包含 workflow_plan 安全回退路径");
        }
        if (!allowedModes.contains(defaultMode)) {
            throw new BusinessException("默认执行模式必须包含在 allowedModes 中");
        }
    }

    private static void validateInteractiveLoopPolicy(JsonNode loop, JsonNode runLlmLimit) {
        if (loop == null || loop.isMissingNode()) {
            return;
        }
        if (!loop.isObject()) {
            throw new BusinessException("executionPolicy.interactiveLoop 必须为 JSON 对象");
        }
        validateInteger(loop, "maxIterations", 1, 50);
        validateInteger(loop, "maxLlmCalls", 1, 50);
        validateInteger(loop, "maxToolCalls", 1, 100);
        validateInteger(loop, "maxTokens", 1, 2_000_000);
        validateInteger(loop, "duplicateToolCallLimit", 1, 10);
        JsonNode cost = loop.get("maxCostAmount");
        if (cost != null && (!cost.isNumber()
                || cost.decimalValue().compareTo(BigDecimal.ZERO) < 0
                || cost.decimalValue().compareTo(BigDecimal.valueOf(100_000)) > 0)) {
            throw new BusinessException("interactiveLoop.maxCostAmount 必须在 0 到 100000 之间");
        }
        JsonNode loopLlmLimit = loop.get("maxLlmCalls");
        if (loopLlmLimit != null && runLlmLimit != null && runLlmLimit.canConvertToInt()
                && loopLlmLimit.asInt() > runLlmLimit.asInt()) {
            throw new BusinessException("interactiveLoop.maxLlmCalls 不能超过单次运行 LLM 上限");
        }
    }

    private static void validateInteger(JsonNode node, String field, int min, int max) {
        JsonNode value = node.get(field);
        if (value != null && (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.asInt() < min || value.asInt() > max)) {
            throw new BusinessException("interactiveLoop." + field + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private static void validateMode(String mode, String label) {
        if (!MODES.contains(mode)) {
            throw new BusinessException(label + "不合法: " + mode);
        }
    }
}
