package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 将 Agent metadata 解析为有界的 Interactive Loop 局部预算。 */
@Service
public class InteractiveLoopBudgetPolicyResolver {

    private static final int DEFAULT_MAX_ITERATIONS = 12;
    private static final int DEFAULT_MAX_LLM_CALLS = 6;
    private static final int DEFAULT_MAX_TOOL_CALLS = 16;
    private static final long DEFAULT_MAX_TOKENS = 50_000;
    private static final BigDecimal DEFAULT_MAX_COST = new BigDecimal("5.00");
    private static final int DEFAULT_DUPLICATE_LIMIT = 2;

    /** 解析策略；非法历史配置回退安全默认值，并服从整次 Run 的 LLM 上限。 */
    public InteractiveLoopBudgetPolicy resolve(ExecutionContext context) {
        JsonNode metadata = context == null || context.agentContext() == null
                ? null : context.agentContext().metadata();
        JsonNode execution = metadata == null ? null : metadata.path("executionPolicy");
        JsonNode loop = execution == null ? null : execution.path("interactiveLoop");
        int runLlmLimit = boundedInt(execution, "maxLlmCallsPerRun", DEFAULT_MAX_LLM_CALLS, 1, 50);
        int loopLlmLimit = boundedInt(loop, "maxLlmCalls", DEFAULT_MAX_LLM_CALLS, 1, 50);
        return new InteractiveLoopBudgetPolicy(
                boundedInt(loop, "maxIterations", DEFAULT_MAX_ITERATIONS, 1, 50),
                Math.min(runLlmLimit, loopLlmLimit),
                boundedInt(loop, "maxToolCalls", DEFAULT_MAX_TOOL_CALLS, 1, 100),
                boundedLong(loop, "maxTokens", DEFAULT_MAX_TOKENS, 1, 2_000_000),
                boundedDecimal(loop, "maxCostAmount", DEFAULT_MAX_COST, BigDecimal.ZERO, BigDecimal.valueOf(100_000)),
                boundedInt(loop, "duplicateToolCallLimit", DEFAULT_DUPLICATE_LIMIT, 1, 10));
    }

    private int boundedInt(JsonNode node, String field, int defaultValue, int min, int max) {
        if (node == null || !node.path(field).canConvertToInt()) {
            return defaultValue;
        }
        int value = node.path(field).asInt();
        return value < min || value > max ? defaultValue : value;
    }

    private long boundedLong(JsonNode node, String field, long defaultValue, long min, long max) {
        if (node == null || !node.path(field).canConvertToLong()) {
            return defaultValue;
        }
        long value = node.path(field).asLong();
        return value < min || value > max ? defaultValue : value;
    }

    private BigDecimal boundedDecimal(JsonNode node,
                                      String field,
                                      BigDecimal defaultValue,
                                      BigDecimal min,
                                      BigDecimal max) {
        JsonNode valueNode = node == null ? null : node.get(field);
        if (valueNode == null || !valueNode.isNumber()) {
            return defaultValue;
        }
        BigDecimal value = valueNode.decimalValue();
        return value.compareTo(min) < 0 || value.compareTo(max) > 0 ? defaultValue : value;
    }
}
