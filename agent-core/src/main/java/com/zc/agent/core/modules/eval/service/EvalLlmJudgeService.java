package com.zc.agent.core.modules.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.EvalLlmProperties;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Eval LLM Judge 服务。
 */
@Service
public class EvalLlmJudgeService {

    private final EvalLlmProperties evalLlmProperties;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final ObjectMapper objectMapper;

    public EvalLlmJudgeService(EvalLlmProperties evalLlmProperties,
                               RuntimeLlmGatewayService runtimeLlmGatewayService,
                               ObjectMapper objectMapper) {
        this.evalLlmProperties = evalLlmProperties;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return evalLlmProperties.isEnabled()
                && hasText(evalLlmProperties.getBaseUrl())
                && hasText(evalLlmProperties.getApiKey())
                && hasText(evalLlmProperties.getModel());
    }

    public JudgeResult judge(EvalJudgePayload payload) {
        if (!enabled()) {
            return JudgeResult.skipped("LLM Judge 未启用");
        }
        try {
            String response = runtimeLlmGatewayService.chatCompletion(
                    """
                    你是 Agent Eval Judge。你必须根据输入严格输出 JSON，不要输出 Markdown。
                    返回结构：
                    {
                      "score": 0-100 的整数,
                      "passed": true/false,
                      "rationale": "一句话结论",
                      "issues": ["问题1", "问题2"]
                    }
                    评判标准：
                    1. 回复是否满足用户问题目标。
                    2. Tool 选择和步骤类型是否基本合理。
                    3. 如果存在明确期望字段，应优先检查是否符合。
                    4. score 反映整体质量，不要永远给高分。
                    """,
                    buildUserPrompt(payload),
                    buildModelContext()
            );
            String normalized = runtimeLlmGatewayService.sanitizeJsonText(response);
            JudgeResponse parsed = objectMapper.readValue(normalized, JudgeResponse.class);
            return new JudgeResult(
                    parsed.score() == null ? 0 : Math.max(0, Math.min(100, parsed.score())),
                    Boolean.TRUE.equals(parsed.passed()),
                    parsed.rationale() == null ? "" : parsed.rationale().trim(),
                    parsed.issues() == null ? List.of() : parsed.issues().stream().filter(item -> item != null && !item.isBlank()).map(String::trim).toList(),
                    false,
                    null
            );
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            return JudgeResult.skipped("LLM Judge 执行失败: " + exception.getMessage());
        }
    }

    private String buildUserPrompt(EvalJudgePayload payload) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "userInput", payload.userInput(),
                "actualReply", payload.actualReply(),
                "expectedReplyKeywords", payload.expectedReplyKeywords(),
                "forbiddenReplyKeywords", payload.forbiddenReplyKeywords(),
                "expectedToolCodes", payload.expectedToolCodes(),
                "actualToolCodes", payload.actualToolCodes(),
                "expectedStepTypes", payload.expectedStepTypes(),
                "actualStepTypes", payload.actualStepTypes(),
                "expectedFinalStatus", payload.expectedFinalStatus(),
                "actualFinalStatus", payload.actualFinalStatus()
        ));
    }

    private ExecutionContext.ModelContext buildModelContext() {
        return new ExecutionContext.ModelContext(
                "EVAL_JUDGE",
                "Eval Judge",
                "custom",
                "chat",
                evalLlmProperties.getBaseUrl(),
                evalLlmProperties.getApiKey(),
                null,
                evalLlmProperties.getModel(),
                evalLlmProperties.getTemperature(),
                evalLlmProperties.getMaxTokens(),
                null,
                null,
                null,
                null,
                null,
                evalLlmProperties.getTimeoutSeconds(),
                "enabled",
                null
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EvalJudgePayload(
            String userInput,
            String actualReply,
            List<String> expectedReplyKeywords,
            List<String> forbiddenReplyKeywords,
            List<String> expectedToolCodes,
            List<String> actualToolCodes,
            List<String> expectedStepTypes,
            List<String> actualStepTypes,
            String expectedFinalStatus,
            String actualFinalStatus
    ) {
    }

    public record JudgeResult(
            Integer score,
            Boolean passed,
            String rationale,
            List<String> issues,
            boolean skipped,
            String skipReason
    ) {
        public static JudgeResult skipped(String reason) {
            return new JudgeResult(null, null, "", List.of(), true, reason);
        }
    }

    private record JudgeResponse(
            Integer score,
            Boolean passed,
            String rationale,
            List<String> issues
    ) {
    }
}
