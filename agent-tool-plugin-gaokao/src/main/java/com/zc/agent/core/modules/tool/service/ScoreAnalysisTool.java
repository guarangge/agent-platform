package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分数分析 Tool。
 */
@Service
public class ScoreAnalysisTool {

    private final ObjectMapper objectMapper;
    private final GaokaoInputParser gaokaoInputParser;
    private final GaokaoKnowledgeService gaokaoKnowledgeService;
    private final GaokaoScoreAnalysisLlmService gaokaoScoreAnalysisLlmService;

    public ScoreAnalysisTool(ObjectMapper objectMapper,
                             GaokaoInputParser gaokaoInputParser,
                             GaokaoKnowledgeService gaokaoKnowledgeService,
                             GaokaoScoreAnalysisLlmService gaokaoScoreAnalysisLlmService) {
        this.objectMapper = objectMapper;
        this.gaokaoInputParser = gaokaoInputParser;
        this.gaokaoKnowledgeService = gaokaoKnowledgeService;
        this.gaokaoScoreAnalysisLlmService = gaokaoScoreAnalysisLlmService;
    }

    /**
     * 执行分数分析。
     *
     * @param tool        Tool 定义
     * @param toolInputText Tool 输入文本
     * @return 执行结果
     */
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        GaokaoInputProfile profile = gaokaoInputParser.parse(toolInputText);
        Integer score = profile.score();
        String level = score == null ? "unknown" : resolveLevel(score);
        List<String> suggestions = buildSuggestions(profile, level);
        List<GaokaoKnowledgeService.UniversityMatch> universities = gaokaoKnowledgeService.searchUniversities(profile, 3);
        List<GaokaoKnowledgeService.MajorMatch> majors = gaokaoKnowledgeService.searchMajors(profile, 3);
        boolean knowledgeCoverageLow = universities.isEmpty() && majors.isEmpty();
        GaokaoScoreAnalysisLlmService.LlmScoreAnalysisResult llmFallback = knowledgeCoverageLow
                ? gaokaoScoreAnalysisLlmService.analyze(executionContext, profile)
                : null;

        StringBuilder responseBuilder = new StringBuilder("已执行分数分析。");
        if (score != null) {
            responseBuilder.append("识别到你的分数约为 ").append(score).append(" 分，当前归类为「").append(levelText(level)).append("」。");
        } else {
            responseBuilder.append("当前未明确识别到有效分数，我先给你一版通用志愿规划建议。");
        }
        if (profile.province() != null || profile.subjectType() != null || profile.rank() != null) {
            responseBuilder.append("\n识别画像：");
            if (profile.province() != null) {
                responseBuilder.append("省份=").append(profile.province()).append("；");
            }
            if (profile.subjectType() != null) {
                responseBuilder.append("科类/选科=").append(profile.subjectType()).append("；");
            }
            if (profile.rank() != null) {
                responseBuilder.append("位次=").append(profile.rank()).append("；");
            }
        }
        responseBuilder.append("\n建议：");
        for (int i = 0; i < suggestions.size(); i++) {
            responseBuilder.append("\n").append(i + 1).append(". ").append(suggestions.get(i));
        }
        if (!universities.isEmpty()) {
            responseBuilder.append("\n候选院校：");
            for (int i = 0; i < universities.size(); i++) {
                GaokaoKnowledgeService.UniversityMatch item = universities.get(i);
                responseBuilder.append("\n").append(i + 1).append(". ")
                        .append(item.university().name())
                        .append("（").append(item.university().city()).append(" / ").append(item.university().tier()).append("）");
                if (item.admissionRule() != null) {
                    responseBuilder.append("，参考分 ").append(item.admissionRule().referenceScore());
                }
                if (!item.reasons().isEmpty()) {
                    responseBuilder.append("，原因：").append(String.join("；", item.reasons()));
                }
            }
        }
        if (!majors.isEmpty()) {
            responseBuilder.append("\n建议关注专业：");
            for (int i = 0; i < majors.size(); i++) {
                GaokaoKnowledgeService.MajorMatch item = majors.get(i);
                responseBuilder.append("\n").append(i + 1).append(". ")
                        .append(item.major().name())
                        .append("：").append(item.major().description());
            }
        }
        if (llmFallback != null) {
            responseBuilder.append("\n知识库未命中足够院校/专业样例，已回退 LLM 补充分析：");
            if (hasText(llmFallback.getSummary())) {
                responseBuilder.append("\n总体判断：").append(llmFallback.getSummary());
            }
            if (llmFallback.getSuggestions() != null && !llmFallback.getSuggestions().isEmpty()) {
                responseBuilder.append("\n补充建议：");
                for (int i = 0; i < llmFallback.getSuggestions().size(); i++) {
                    responseBuilder.append("\n").append(i + 1).append(". ").append(llmFallback.getSuggestions().get(i));
                }
            }
            if (llmFallback.getRecommendedDirections() != null && !llmFallback.getRecommendedDirections().isEmpty()) {
                responseBuilder.append("\n建议方向：")
                        .append(String.join("、", llmFallback.getRecommendedDirections()));
            }
            if (llmFallback.getRiskNotes() != null && !llmFallback.getRiskNotes().isEmpty()) {
                responseBuilder.append("\n补充风险：")
                        .append(String.join("；", llmFallback.getRiskNotes()));
            }
            if (llmFallback.getMissingInformation() != null && !llmFallback.getMissingInformation().isEmpty()) {
                responseBuilder.append("\n建议补充：")
                        .append(String.join("、", llmFallback.getMissingInformation()));
            }
        }
        responseBuilder.append("\n说明：当前结果基于内置高考知识库和规则分析，最终填报仍需结合目标省份当年官方数据。");

        String outputSnapshot = toJsonText(new ScoreAnalysisSnapshot(
                profile,
                level,
                suggestions,
                universities,
                majors,
                knowledgeCoverageLow,
                toLlmFallbackSnapshot(llmFallback)
        ));
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已执行高考分数分析",
                responseBuilder.toString(),
                outputSnapshot
        );
    }

    private String resolveLevel(int score) {
        if (score >= 650) {
            return "high";
        }
        if (score >= 580) {
            return "upper_middle";
        }
        if (score >= 500) {
            return "middle";
        }
        return "basic";
    }

    private List<String> buildSuggestions(GaokaoInputProfile profile, String level) {
        List<String> suggestions = new ArrayList<>();
        Integer score = profile.score();
        if (score == null) {
            suggestions.add("先明确省份、科类、分数、排名，再做院校和专业范围收敛。");
            suggestions.add("按“冲 / 稳 / 保”三层去配学校，不要只列理想院校。");
            suggestions.add("同时把专业接受度、城市偏好、是否服从调剂一起纳入。");
            return suggestions;
        }

        if ("high".equals(level)) {
            suggestions.add("优先用位次和近三年录取线做精筛，重点关注头部院校的专业分流和调剂风险。");
            suggestions.add("组合上可以少量“冲高”，但要保留 2 到 3 所稳定院校，避免全是高波动志愿。");
        } else if ("upper_middle".equals(level)) {
            suggestions.add("建议重点做“稳”的学校池，把录取位次接近的院校和专业拉齐比较。");
            suggestions.add("如果更重视专业，建议适当下调学校层级，换取更高专业确定性。");
        } else if ("middle".equals(level)) {
            suggestions.add("重点关注区域性强校和特色专业，不要只盯综合排名。");
            suggestions.add("志愿结构上建议扩大备选数量，降低单一学校落空风险。");
        } else {
            suggestions.add("优先保证录取安全边界，先建立可录取院校池，再逐步筛专业。");
            suggestions.add("如果对本科 / 专科 / 专项计划有差异诉求，建议提前拆开路径评估。");
        }

        if (profile.rawText().contains("专业") || !profile.requestedMajors().isEmpty()) {
            suggestions.add("你已经提到专业诉求，后续应把专业录取分、是否热门、调剂方向一起核对。");
        }
        if (profile.rawText().contains("城市") || profile.rawText().contains("省") || profile.city() != null) {
            suggestions.add("你对地域有关注，后续应同步比较城市机会、学科资源和生活成本。");
        }
        if (profile.rank() == null) {
            suggestions.add("当前还缺位次信息，后续做院校精筛时建议优先补齐。");
        }
        suggestions.add("下一步建议补充省份、排名和目标专业，我可以继续细化院校范围。");
        return suggestions;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String levelText(String level) {
        return switch (level) {
            case "high" -> "高分段";
            case "upper_middle" -> "中高分段";
            case "middle" -> "中分段";
            case "basic" -> "基础分段";
            default -> "未知分段";
        };
    }

    private LlmFallbackSnapshot toLlmFallbackSnapshot(GaokaoScoreAnalysisLlmService.LlmScoreAnalysisResult llmFallback) {
        if (llmFallback == null) {
            return new LlmFallbackSnapshot(false, "", "", List.of(), List.of(), List.of(), List.of());
        }
        return new LlmFallbackSnapshot(
                true,
                safeText(llmFallback.getSummary()),
                safeText(llmFallback.getStrategyLevel()),
                safeList(llmFallback.getSuggestions()),
                safeList(llmFallback.getRecommendedDirections()),
                safeList(llmFallback.getRiskNotes()),
                safeList(llmFallback.getMissingInformation())
        );
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("分数分析结果序列化失败");
        }
    }

    private record ScoreAnalysisSnapshot(
            GaokaoInputProfile profile,
            String level,
            List<String> suggestions,
            List<GaokaoKnowledgeService.UniversityMatch> candidateUniversities,
            List<GaokaoKnowledgeService.MajorMatch> candidateMajors,
            boolean knowledgeCoverageLow,
            LlmFallbackSnapshot llmFallback
    ) {
    }

    private record LlmFallbackSnapshot(
            boolean applied,
            String summary,
            String strategyLevel,
            List<String> suggestions,
            List<String> recommendedDirections,
            List<String> riskNotes,
            List<String> missingInformation
    ) {
    }
}
