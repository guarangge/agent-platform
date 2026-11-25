package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 高考知识库服务。
 */
@Service
public class GaokaoKnowledgeService {

    private static final String KNOWLEDGE_FILE = "gaokao/knowledge-base.json";

    private final ObjectMapper objectMapper;
    private final AtomicReference<GaokaoKnowledgeBundle> cache = new AtomicReference<>();

    public GaokaoKnowledgeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索政策条目。
     *
     * @param profile 输入画像
     * @param limit   返回数量
     * @return 命中条目
     */
    public List<PolicyEntry> searchPolicies(GaokaoInputProfile profile, int limit) {
        String province = normalize(profile.province());
        return loadBundle().policies().stream()
                .map(item -> Map.entry(item, scorePolicy(item, profile, province)))
                .filter(item -> item.getValue() > 0)
                .sorted(Map.Entry.<PolicyEntry, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 搜索院校条目。
     *
     * @param profile 输入画像
     * @param limit   返回数量
     * @return 命中院校
     */
    public List<UniversityMatch> searchUniversities(GaokaoInputProfile profile, int limit) {
        return loadBundle().universities().stream()
                .map(item -> scoreUniversity(item, profile))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(UniversityMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 搜索专业条目。
     *
     * @param profile 输入画像
     * @param limit   返回数量
     * @return 命中专业
     */
    public List<MajorMatch> searchMajors(GaokaoInputProfile profile, int limit) {
        return loadBundle().majors().stream()
                .map(item -> scoreMajor(item, profile))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(MajorMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    private int scorePolicy(PolicyEntry entry, GaokaoInputProfile profile, String province) {
        String entryProvince = normalize(entry.province());
        if (province != null
                && entryProvince != null
                && !province.equals(entryProvince)
                && !"全国".equals(entry.province())) {
            return 0;
        }
        int score = 0;
        if (province != null && province.equals(entryProvince)) {
            score += 8;
        }
        if ("全国".equals(entry.province())) {
            score += 2;
        }
        score += countMatches(profile.rawText(), entry.title(), 4);
        score += countMatches(profile.rawText(), entry.category(), 2);
        for (String keyword : entry.keywords()) {
            score += countMatches(profile.rawText(), keyword, 3);
        }
        return score;
    }

    private UniversityMatch scoreUniversity(UniversityEntry entry, GaokaoInputProfile profile) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        score += countMatches(profile.rawText(), entry.name(), 10);
        if (entry.tags() != null) {
            for (String tag : entry.tags()) {
                int matched = countMatches(profile.rawText(), tag, 3);
                if (matched > 0) {
                    score += matched;
                    reasons.add("命中院校标签：" + tag);
                }
            }
        }
        if (profile.city() != null && profile.city().equals(entry.city())) {
            score += 6;
            reasons.add("符合城市偏好：" + entry.city());
        } else if (profile.rawText() != null && profile.rawText().contains(entry.city())) {
            score += 6;
            reasons.add("命中城市诉求：" + entry.city());
        }
        if (profile.province() != null && profile.province().equals(entry.province())) {
            score += 3;
            reasons.add("符合省内偏好");
        }
        if (entry.strongMajors() != null && profile.requestedMajors() != null) {
            for (String major : profile.requestedMajors()) {
                if (entry.strongMajors().stream().anyMatch(item -> item.contains(major) || major.contains(item))) {
                    score += 6;
                    reasons.add("强势专业匹配：" + major);
                }
            }
        }
        AdmissionRule matchedRule = findAdmissionRule(entry, profile);
        if (matchedRule != null && profile.score() != null) {
            int delta = profile.score() - matchedRule.referenceScore();
            if (delta >= 20) {
                score += 10;
                reasons.add("分数高于参考线较多，偏保守");
            } else if (delta >= 5) {
                score += 8;
                reasons.add("分数高于参考线，偏稳妥");
            } else if (delta >= -8) {
                score += 6;
                reasons.add("分数接近参考线，偏冲刺");
            } else {
                score += 2;
                reasons.add("分数低于参考线，风险较高");
            }
        }
        if (matchedRule != null && profile.rank() != null && matchedRule.referenceRank() != null) {
            int delta = matchedRule.referenceRank() - profile.rank();
            if (delta >= 8000) {
                score += 10;
                reasons.add("位次明显优于参考线，录取把握较高");
            } else if (delta >= 2000) {
                score += 8;
                reasons.add("位次优于参考线，整体较稳");
            } else if (delta >= -3000) {
                score += 6;
                reasons.add("位次接近参考线，可作为冲稳之间选择");
            } else {
                score += 2;
                reasons.add("位次弱于参考线，风险较高");
            }
        }
        if (matchedRule == null && profile.score() != null) {
            reasons.add("当前院校仅命中画像偏好，缺少同省参考线样例");
        }
        return new UniversityMatch(entry, matchedRule, score, reasons);
    }

    private MajorMatch scoreMajor(MajorEntry entry, GaokaoInputProfile profile) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        score += countMatches(profile.rawText(), entry.name(), 10);
        score += countMatches(profile.rawText(), entry.category(), 2);
        if (entry.tags() != null) {
            for (String tag : entry.tags()) {
                int matched = countMatches(profile.rawText(), tag, 3);
                if (matched > 0) {
                    score += matched;
                    reasons.add("命中专业标签：" + tag);
                }
            }
        }
        if (profile.subjectType() != null && entry.recommendedSubjects() != null
                && entry.recommendedSubjects().stream().anyMatch(item -> item.contains(profile.subjectType()))) {
            score += 6;
            reasons.add("选科方向匹配：" + profile.subjectType());
        }
        if (profile.score() != null && entry.referenceScoreMin() != null) {
            int delta = profile.score() - entry.referenceScoreMin();
            if (delta >= 30) {
                score += 8;
                reasons.add("分数达到专业参考线以上较多");
            } else if (delta >= 0) {
                score += 6;
                reasons.add("分数达到专业参考线");
            } else {
                score += 2;
                reasons.add("分数低于专业参考线，需谨慎");
            }
        }
        return new MajorMatch(entry, score, reasons);
    }

    private AdmissionRule findAdmissionRule(UniversityEntry entry, GaokaoInputProfile profile) {
        if (entry.admissionRules() == null || entry.admissionRules().isEmpty()) {
            return null;
        }
        String profileProvince = normalize(profile.province());
        String profileSubject = normalize(profile.subjectType());
        return entry.admissionRules().stream()
                .filter(item -> profileProvince == null || profileProvince.equals(normalize(item.province())))
                .filter(item -> profileSubject == null || item.subjectType() == null
                        || normalize(item.subjectType()).contains(profileSubject)
                        || profileSubject.contains(normalize(item.subjectType())))
                .findFirst()
                .orElse(entry.admissionRules().get(0));
    }

    private int countMatches(String source, String target, int weight) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return 0;
        }
        return normalize(source).contains(normalize(target)) ? weight : 0;
    }

    private String normalize(String text) {
        return text == null ? null : text.trim().toLowerCase(Locale.ROOT);
    }

    private GaokaoKnowledgeBundle loadBundle() {
        GaokaoKnowledgeBundle cached = cache.get();
        if (cached != null) {
            return cached;
        }
        synchronized (cache) {
            GaokaoKnowledgeBundle secondCheck = cache.get();
            if (secondCheck != null) {
                return secondCheck;
            }
            try (InputStream inputStream = new ClassPathResource(KNOWLEDGE_FILE).getInputStream()) {
                GaokaoKnowledgeBundle loaded = objectMapper.readValue(inputStream, new TypeReference<>() {
                });
                cache.set(loaded);
                return loaded;
            } catch (IOException exception) {
                throw new BusinessException("高考知识库加载失败: " + exception.getMessage());
            }
        }
    }

    public record GaokaoKnowledgeBundle(
            List<PolicyEntry> policies,
            List<UniversityEntry> universities,
            List<MajorEntry> majors
    ) {
    }

    public record PolicyEntry(
            String policyCode,
            String title,
            String province,
            String category,
            List<String> keywords,
            String summary,
            String content,
            List<String> riskNotes
    ) {
    }

    public record UniversityEntry(
            String universityCode,
            String name,
            String province,
            String city,
            String tier,
            String schoolType,
            List<String> tags,
            List<String> strongMajors,
            List<AdmissionRule> admissionRules,
            String summary
    ) {
    }

    public record AdmissionRule(
            String province,
            String subjectType,
            Integer referenceScore,
            Integer referenceRank,
            String adviceLevel,
            String note
    ) {
    }

    public record MajorEntry(
            String majorCode,
            String name,
            String category,
            List<String> tags,
            List<String> recommendedSubjects,
            Integer referenceScoreMin,
            String description,
            List<String> careerDirections,
            List<String> riskNotes
    ) {
    }

    public record UniversityMatch(
            UniversityEntry university,
            AdmissionRule admissionRule,
            int score,
            List<String> reasons
    ) {
    }

    public record MajorMatch(
            MajorEntry major,
            int score,
            List<String> reasons
    ) {
    }
}
