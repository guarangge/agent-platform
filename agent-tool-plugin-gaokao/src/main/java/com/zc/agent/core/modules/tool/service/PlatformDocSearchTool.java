package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.ToolProperties;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台默认文档检索 Tool。
 */
@Service
public class PlatformDocSearchTool {

    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_-]{2,}");
    private static final List<String> GAOKAO_QUERY_HINTS = List.of(
            "高考", "志愿", "位次", "投档", "调剂", "平行志愿", "专业组", "院校", "大学", "选科", "分数"
    );
    private static final Set<String> GENERIC_KEYWORDS = Set.of(
            "用户问题", "前序结果", "已执行", "什么意思", "是什么", "怎么", "如何", "注意什么", "考生", "说明"
    );

    private final ToolProperties toolProperties;
    private final String gaokaoDocSearchRoot;
    private final ObjectMapper objectMapper;
    private final GaokaoInputParser gaokaoInputParser;
    private final GaokaoKnowledgeService gaokaoKnowledgeService;
    private final DocSearchLlmFallbackService docSearchLlmFallbackService;

    public PlatformDocSearchTool(ToolProperties toolProperties,
                                 @Value("${agent.plugin.gaokao.doc-search-root:../docs/gaokao}") String gaokaoDocSearchRoot,
                                 ObjectMapper objectMapper,
                                 GaokaoInputParser gaokaoInputParser,
                                 GaokaoKnowledgeService gaokaoKnowledgeService,
                                 DocSearchLlmFallbackService docSearchLlmFallbackService) {
        this.toolProperties = toolProperties;
        this.gaokaoDocSearchRoot = gaokaoDocSearchRoot;
        this.objectMapper = objectMapper;
        this.gaokaoInputParser = gaokaoInputParser;
        this.gaokaoKnowledgeService = gaokaoKnowledgeService;
        this.docSearchLlmFallbackService = docSearchLlmFallbackService;
    }

    /**
     * 执行文档检索。
     *
     * @param tool        Tool 定义
     * @param toolInputText Tool 输入文本
     * @return 执行结果
     */
    public ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText) {
        GaokaoInputProfile profile = gaokaoInputParser.parse(toolInputText);
        List<String> keywords = extractKeywords(toolInputText, profile);
        boolean gaokaoQuery = isGaokaoQuery(profile);
        List<GaokaoKnowledgeService.PolicyEntry> policyHits = gaokaoQuery
                ? gaokaoKnowledgeService.searchPolicies(profile, 3)
                : List.of();
        List<GaokaoKnowledgeService.UniversityMatch> universityHits = gaokaoQuery && shouldIncludeUniversityHits(profile)
                ? gaokaoKnowledgeService.searchUniversities(profile, 3)
                : List.of();
        List<GaokaoKnowledgeService.MajorMatch> majorHits = gaokaoQuery && shouldIncludeMajorHits(profile)
                ? gaokaoKnowledgeService.searchMajors(profile, 3)
                : List.of();
        boolean hasStructuredGaokaoHits = !policyHits.isEmpty() || !universityHits.isEmpty() || !majorHits.isEmpty();
        List<SearchHit> hits = searchAcrossRoots(keywords, gaokaoQuery, !hasStructuredGaokaoHits);
        boolean knowledgeMiss = policyHits.isEmpty() && universityHits.isEmpty() && majorHits.isEmpty() && hits.isEmpty();
        String llmFallbackAnswer = knowledgeMiss
                ? docSearchLlmFallbackService.answer(executionContext, toolInputText, gaokaoQuery)
                : null;

        String responseText = buildResponseText(gaokaoQuery, policyHits, universityHits, majorHits, hits, llmFallbackAnswer);
        String outputSnapshot = toJsonText(new DocSearchSnapshot(
                keywords,
                gaokaoQuery,
                hits.stream().map(hit -> new DocSearchHitSnapshot(hit.rootLabel(), hit.fileName(), hit.relativePath(), hit.lineText(), hit.score())).toList(),
                policyHits,
                universityHits,
                majorHits,
                llmFallbackAnswer
        ));
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                resolveSummary(gaokaoQuery, llmFallbackAnswer),
                responseText,
                outputSnapshot
        );
    }

    private List<SearchHit> searchAcrossRoots(List<String> keywords, boolean gaokaoQuery, boolean allowProjectFallback) {
        List<Map.Entry<String, Path>> roots = resolveRoots(gaokaoQuery);
        if (roots.isEmpty()) {
            throw new BusinessException("文档检索目录不存在，请检查 core.tools 配置");
        }
        if (!gaokaoQuery) {
            return searchHits(roots, keywords);
        }
        List<Map.Entry<String, Path>> gaokaoRoots = roots.stream()
                .filter(root -> "gaokao_docs".equals(root.getKey()))
                .toList();
        List<SearchHit> gaokaoHits = searchHits(gaokaoRoots, keywords);
        if (!gaokaoHits.isEmpty() || !allowProjectFallback) {
            return gaokaoHits;
        }
        List<Map.Entry<String, Path>> fallbackRoots = roots.stream()
                .filter(root -> !"gaokao_docs".equals(root.getKey()))
                .toList();
        return searchHits(fallbackRoots, keywords);
    }

    private List<SearchHit> searchHits(List<Map.Entry<String, Path>> roots, List<String> keywords) {
        List<SearchHit> hits = new ArrayList<>();
        for (Map.Entry<String, Path> rootEntry : roots) {
            Path root = rootEntry.getValue();
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .forEach(path -> collectHits(rootEntry.getKey(), root, path, keywords, hits));
            } catch (IOException exception) {
                throw new BusinessException("执行文档检索失败");
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(SearchHit::score).reversed())
                .limit(5)
                .toList();
    }

    private List<Map.Entry<String, Path>> resolveRoots(boolean gaokaoQuery) {
        List<Map.Entry<String, Path>> roots = new ArrayList<>();
        if (gaokaoQuery) {
            addRoot(roots, "gaokao_docs", gaokaoDocSearchRoot);
        }
        addRoot(roots, "project_docs", toolProperties.getDocSearchRoot());
        return roots;
    }

    private void addRoot(List<Map.Entry<String, Path>> roots, String label, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            roots.add(Map.entry(label, path));
        }
    }

    private void collectHits(String rootLabel, Path root, Path path, List<String> keywords, List<SearchHit> hits) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int bestScore = 0;
            String bestLine = null;
            for (String line : lines) {
                int score = scoreLine(path.getFileName().toString(), line, keywords);
                if (score > bestScore) {
                    bestScore = score;
                    bestLine = line.trim();
                }
            }
            if (bestScore > 0 && bestLine != null && !bestLine.isBlank()) {
                hits.add(new SearchHit(rootLabel, path.getFileName().toString(), root.relativize(path).toString(), bestLine, bestScore));
            }
        } catch (IOException ignored) {
        }
    }

    private String buildResponseText(boolean gaokaoQuery,
                                     List<GaokaoKnowledgeService.PolicyEntry> policyHits,
                                     List<GaokaoKnowledgeService.UniversityMatch> universityHits,
                                     List<GaokaoKnowledgeService.MajorMatch> majorHits,
                                     List<SearchHit> hits,
                                     String llmFallbackAnswer) {
        StringBuilder builder = new StringBuilder();
        if (gaokaoQuery) {
            builder.append("已优先执行高考知识库 / 高考文档检索。");
            if (!policyHits.isEmpty()) {
                builder.append("\n政策命中：");
                for (int i = 0; i < policyHits.size(); i++) {
                    GaokaoKnowledgeService.PolicyEntry item = policyHits.get(i);
                    builder.append("\n").append(i + 1).append(". ")
                            .append(item.title())
                            .append("：").append(item.summary());
                }
            }
            if (!universityHits.isEmpty()) {
                builder.append("\n院校命中：");
                for (int i = 0; i < universityHits.size(); i++) {
                    GaokaoKnowledgeService.UniversityMatch item = universityHits.get(i);
                    builder.append("\n").append(i + 1).append(". ")
                            .append(item.university().name())
                            .append("（").append(item.university().city()).append(" / ").append(item.university().tier()).append("）");
                }
            }
            if (!majorHits.isEmpty()) {
                builder.append("\n专业命中：");
                for (int i = 0; i < majorHits.size(); i++) {
                    GaokaoKnowledgeService.MajorMatch item = majorHits.get(i);
                    builder.append("\n").append(i + 1).append(". ")
                            .append(item.major().name())
                            .append("：").append(item.major().description());
                }
            }
        } else {
            builder.append("已根据你的问题执行项目文档检索。");
        }
        if (!hits.isEmpty()) {
            builder.append("\n文档命中：");
            for (int i = 0; i < hits.size(); i++) {
                SearchHit hit = hits.get(i);
                builder.append("\n").append(i + 1)
                        .append(". [").append(hit.rootLabel()).append(" / ").append(hit.fileName()).append("] ")
                        .append(hit.lineText());
            }
        }
        if ((policyHits == null || policyHits.isEmpty())
                && (universityHits == null || universityHits.isEmpty())
                && (majorHits == null || majorHits.isEmpty())
                && hits.isEmpty()) {
            if (hasText(llmFallbackAnswer)) {
                return (gaokaoQuery
                        ? "已尝试优先执行高考知识库 / 高考文档检索，当前本地未命中足够内容，已回退 LLM 补充说明。"
                        : "已尝试执行项目文档检索，当前本地未命中足够内容，已回退 LLM 补充说明。")
                        + "\nLLM 补充：\n"
                        + llmFallbackAnswer;
            }
            return gaokaoQuery
                    ? "已尝试优先执行高考知识库 / 高考文档检索，但当前未命中足够内容。建议补充省份、分数、位次、专业或政策关键词。"
                    : "已尝试执行文档检索，但当前未在项目文档中命中足够相关的内容。你可以换更具体的关键词，例如：数据库、接口、原型、工作流。";
        }
        return builder.toString();
    }

    private int scoreLine(String fileName, String line, List<String> keywords) {
        String normalizedLine = line.toLowerCase(Locale.ROOT);
        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if (normalizedLine.contains(normalizedKeyword)) {
                score += Math.max(2, normalizedKeyword.length());
            }
            if (normalizedFileName.contains(normalizedKeyword)) {
                score += 3;
            }
        }
        return score;
    }

    private List<String> extractKeywords(String userMessage, GaokaoInputProfile profile) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(userMessage);
        while (matcher.find()) {
            addKeywordCandidate(keywords, matcher.group());
        }
        if (profile != null) {
            addKeywordCandidate(keywords, profile.province());
            addKeywordCandidate(keywords, profile.city());
            if (profile.requestedMajors() != null) {
                profile.requestedMajors().forEach(item -> addKeywordCandidate(keywords, item));
            }
            if (profile.requestedUniversities() != null) {
                profile.requestedUniversities().forEach(item -> addKeywordCandidate(keywords, item));
            }
        }
        for (String hint : GAOKAO_QUERY_HINTS) {
            if (userMessage != null && userMessage.contains(hint)) {
                addKeywordCandidate(keywords, hint);
            }
        }
        if (keywords.isEmpty()) {
            addKeywordCandidate(keywords, userMessage);
        }
        return new ArrayList<>(keywords);
    }

    private void addKeywordCandidate(LinkedHashSet<String> keywords, String rawKeyword) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return;
        }
        String normalized = rawKeyword.trim();
        if (normalized.isBlank() || GENERIC_KEYWORDS.contains(normalized)) {
            return;
        }
        String compact = normalized.replace("用户问题", "")
                .replace("前序结果", "")
                .replace("已执行高考政策检索", "")
                .replace("已执行", "")
                .trim();
        if (!compact.isBlank() && compact.length() <= 16 && !GENERIC_KEYWORDS.contains(compact)) {
            keywords.add(compact);
        }
        for (String segment : compact.split("[，。！？；：、\\s\\n]+")) {
            String item = segment.trim();
            if (!item.isBlank() && item.length() <= 12 && !GENERIC_KEYWORDS.contains(item)) {
                keywords.add(item);
            }
            for (String hint : GAOKAO_QUERY_HINTS) {
                if (item.contains(hint)) {
                    keywords.add(hint);
                }
            }
        }
    }

    private boolean shouldIncludeUniversityHits(GaokaoInputProfile profile) {
        if (profile == null) {
            return false;
        }
        if (profile.score() != null || profile.rank() != null) {
            return true;
        }
        if (profile.requestedUniversities() != null && !profile.requestedUniversities().isEmpty()) {
            return true;
        }
        String text = profile.rawText() == null ? "" : profile.rawText();
        return text.contains("大学") || text.contains("院校") || text.contains("学校") || text.contains("985")
                || text.contains("211") || text.contains("双一流");
    }

    private boolean shouldIncludeMajorHits(GaokaoInputProfile profile) {
        if (profile == null) {
            return false;
        }
        if (profile.requestedMajors() != null && !profile.requestedMajors().isEmpty()) {
            return true;
        }
        String text = profile.rawText() == null ? "" : profile.rawText();
        return text.contains("专业推荐") || text.contains("专业选择") || text.contains("想学什么专业");
    }

    private boolean isGaokaoQuery(GaokaoInputProfile profile) {
        if (profile.score() != null || profile.rank() != null || profile.province() != null) {
            return true;
        }
        if (!profile.requestedMajors().isEmpty() || !profile.requestedUniversities().isEmpty()) {
            return true;
        }
        String text = profile.rawText() == null ? "" : profile.rawText();
        return GAOKAO_QUERY_HINTS.stream().anyMatch(text::contains);
    }

    private String resolveSummary(boolean gaokaoQuery, String llmFallbackAnswer) {
        if (hasText(llmFallbackAnswer)) {
            return gaokaoQuery ? "已执行高考知识检索并回退 LLM 补充" : "已执行项目文档检索并回退 LLM 补充";
        }
        return gaokaoQuery ? "已执行高考知识检索" : "已执行项目文档检索";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("文档检索结果序列化失败");
        }
    }

    private record SearchHit(String rootLabel, String fileName, String relativePath, String lineText, int score) {
    }

    private record DocSearchSnapshot(
            List<String> keywords,
            boolean gaokaoQuery,
            List<DocSearchHitSnapshot> hits,
            List<GaokaoKnowledgeService.PolicyEntry> policyHits,
            List<GaokaoKnowledgeService.UniversityMatch> universityHits,
            List<GaokaoKnowledgeService.MajorMatch> majorHits,
            String llmFallbackAnswer
    ) {
    }

    private record DocSearchHitSnapshot(String rootLabel, String fileName, String relativePath, String lineText, int score) {
    }
}
