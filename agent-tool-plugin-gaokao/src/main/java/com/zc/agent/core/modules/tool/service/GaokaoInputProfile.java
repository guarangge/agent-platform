package com.zc.agent.core.modules.tool.service;

import java.util.List;

/**
 * 高考场景下从用户输入中提取的结构化画像。
 *
 * @param rawText             原始输入
 * @param province            省份
 * @param city                城市偏好
 * @param score               分数
 * @param rank                位次
 * @param subjectType         科类 / 选科方向
 * @param requestedMajors     命中的专业诉求
 * @param requestedUniversities 命中的院校诉求
 * @param keywords            检索关键词
 */
public record GaokaoInputProfile(
        String rawText,
        String province,
        String city,
        Integer score,
        Integer rank,
        String subjectType,
        List<String> requestedMajors,
        List<String> requestedUniversities,
        List<String> keywords
) {
}
