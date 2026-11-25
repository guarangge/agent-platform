package com.zc.agent.core.modules.tool.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高考输入解析器。
 */
@Component
public class GaokaoInputParser {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3})(?!\\d)");
    private static final Pattern RANK_PATTERN = Pattern.compile("(位次|排名|省排|全省排名)(?:大概|大约|约|在|为|是|：|:|\\s)*?(\\d{1,7})");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_-]{2,}");

    private static final Map<String, String> PROVINCE_KEYWORDS = Map.ofEntries(
            Map.entry("北京", "北京"),
            Map.entry("天津", "天津"),
            Map.entry("河北", "河北"),
            Map.entry("山西", "山西"),
            Map.entry("内蒙古", "内蒙古"),
            Map.entry("辽宁", "辽宁"),
            Map.entry("吉林", "吉林"),
            Map.entry("黑龙江", "黑龙江"),
            Map.entry("上海", "上海"),
            Map.entry("江苏", "江苏"),
            Map.entry("浙江", "浙江"),
            Map.entry("安徽", "安徽"),
            Map.entry("福建", "福建"),
            Map.entry("江西", "江西"),
            Map.entry("山东", "山东"),
            Map.entry("河南", "河南"),
            Map.entry("湖北", "湖北"),
            Map.entry("湖南", "湖南"),
            Map.entry("广东", "广东"),
            Map.entry("广西", "广西"),
            Map.entry("海南", "海南"),
            Map.entry("重庆", "重庆"),
            Map.entry("四川", "四川"),
            Map.entry("贵州", "贵州"),
            Map.entry("云南", "云南"),
            Map.entry("陕西", "陕西"),
            Map.entry("甘肃", "甘肃"),
            Map.entry("青海", "青海"),
            Map.entry("宁夏", "宁夏"),
            Map.entry("新疆", "新疆")
    );

    private static final List<String> SUBJECT_PATTERNS = List.of(
            "物理类", "历史类", "理科", "文科", "物化生", "物生地", "物化地", "史政地", "史化地", "不限"
    );

    private static final List<String> CITY_PATTERNS = List.of(
            "北京", "上海", "广州", "深圳", "南京", "杭州", "苏州", "武汉", "成都", "西安", "天津", "长沙"
    );

    /**
     * 从输入中提取高考画像。
     *
     * @param userMessage 用户输入
     * @return 结构化画像
     */
    public GaokaoInputProfile parse(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        return new GaokaoInputProfile(
                normalized,
                detectProvince(normalized),
                detectCity(normalized),
                detectScore(normalized),
                detectRank(normalized),
                detectSubjectType(normalized),
                detectRequestedMajors(normalized),
                detectRequestedUniversities(normalized),
                extractKeywords(normalized)
        );
    }

    private String detectProvince(String text) {
        for (Map.Entry<String, String> entry : PROVINCE_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String detectCity(String text) {
        for (String city : CITY_PATTERNS) {
            if (text.contains(city)) {
                return city;
            }
        }
        return null;
    }

    private Integer detectScore(String text) {
        Matcher matcher = SCORE_PATTERN.matcher(text);
        while (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            if (score >= 200 && score <= 750) {
                return score;
            }
        }
        return null;
    }

    private Integer detectRank(String text) {
        Matcher matcher = RANK_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return null;
    }

    private String detectSubjectType(String text) {
        for (String pattern : SUBJECT_PATTERNS) {
            if (text.contains(pattern)) {
                return pattern;
            }
        }
        return null;
    }

    private List<String> detectRequestedMajors(String text) {
        return detectKnownTerms(text, List.of(
                "计算机", "软件工程", "人工智能", "电子信息", "临床医学", "口腔医学", "法学",
                "汉语言", "师范", "金融", "会计", "自动化", "电气", "新闻传播", "数学"
        ));
    }

    private List<String> detectRequestedUniversities(String text) {
        return detectKnownTerms(text, List.of(
                "清华", "北大", "复旦", "上交", "浙大", "南京大学", "东南大学", "深圳大学",
                "华南师范", "暨南大学", "南京邮电", "杭州电子科技", "南京信息工程"
        ));
    }

    private List<String> detectKnownTerms(String text, List<String> candidates) {
        List<String> terms = new ArrayList<>();
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                terms.add(candidate);
            }
        }
        return terms;
    }

    private List<String> extractKeywords(String text) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String item = matcher.group().trim().toLowerCase(Locale.ROOT);
            if (!item.isBlank()) {
                keywords.add(item);
            }
        }
        return new ArrayList<>(keywords);
    }
}
