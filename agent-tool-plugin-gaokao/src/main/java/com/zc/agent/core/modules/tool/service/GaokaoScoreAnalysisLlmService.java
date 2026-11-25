package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 高考分数分析 LLM 回退服务。
 */
@Service
public class GaokaoScoreAnalysisLlmService {

    private static final Logger log = LoggerFactory.getLogger(GaokaoScoreAnalysisLlmService.class);

    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final ObjectMapper objectMapper;

    public GaokaoScoreAnalysisLlmService(RuntimeLlmGatewayService runtimeLlmGatewayService,
                                         ObjectMapper objectMapper) {
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.objectMapper = objectMapper;
    }

    /**
     * 当知识库无法提供足够命中结果时，回退到运行时模型做方向性分析。
     *
     * @param executionContext 运行时上下文
     * @param profile          输入画像
     * @return LLM 结果；失败时返回 null
     */
    public LlmScoreAnalysisResult analyze(ExecutionContext executionContext, GaokaoInputProfile profile) {
        if (executionContext == null || !executionContext.hasRunnableModel()) {
            return null;
        }
        try {
            String responseText = runtimeLlmGatewayService.chatCompletion(
                    buildSystemPrompt(),
                    buildUserPrompt(profile),
                    executionContext.modelContext()
            );
            String sanitized = runtimeLlmGatewayService.sanitizeJsonText(responseText);
            return objectMapper.readValue(sanitized, LlmScoreAnalysisResult.class);
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            log.warn("高考分数分析 LLM 回退失败，继续使用规则结果：{}", exception.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt() {
        return """
                你是高考志愿填报分析助手。
                当前任务是在本地知识库未命中足够院校或专业样例时，基于用户输入给出谨慎的补充分析。
                约束：
                1. 不得编造具体院校录取分、位次、招生计划。
                2. 只给方向性建议、冲稳保策略和补充信息建议。
                3. 如果信息不足，要明确指出缺失项。
                4. 输出严格 JSON，不要 Markdown，不要解释。
                JSON 结构：
                {
                  "summary": "一句总体判断",
                  "strategyLevel": "sprint|balance|safe|unknown",
                  "suggestions": ["建议1", "建议2"],
                  "recommendedDirections": ["方向1", "方向2"],
                  "riskNotes": ["风险1", "风险2"],
                  "missingInformation": ["缺失项1", "缺失项2"]
                }
                """;
    }

    private String buildUserPrompt(GaokaoInputProfile profile) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "rawText", profile.rawText(),
                "province", profile.province(),
                "city", profile.city(),
                "score", profile.score(),
                "rank", profile.rank(),
                "subjectType", profile.subjectType(),
                "requestedMajors", profile.requestedMajors(),
                "requestedUniversities", profile.requestedUniversities()
        ));
    }

    /**
     * LLM 回退结果。
     */
    public static class LlmScoreAnalysisResult {

        private String summary;
        private String strategyLevel;
        private List<String> suggestions;
        private List<String> recommendedDirections;
        private List<String> riskNotes;
        private List<String> missingInformation;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getStrategyLevel() {
            return strategyLevel;
        }

        public void setStrategyLevel(String strategyLevel) {
            this.strategyLevel = strategyLevel;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<String> suggestions) {
            this.suggestions = suggestions;
        }

        public List<String> getRecommendedDirections() {
            return recommendedDirections;
        }

        public void setRecommendedDirections(List<String> recommendedDirections) {
            this.recommendedDirections = recommendedDirections;
        }

        public List<String> getRiskNotes() {
            return riskNotes;
        }

        public void setRiskNotes(List<String> riskNotes) {
            this.riskNotes = riskNotes;
        }

        public List<String> getMissingInformation() {
            return missingInformation;
        }

        public void setMissingInformation(List<String> missingInformation) {
            this.missingInformation = missingInformation;
        }
    }
}
