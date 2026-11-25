package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 高考院校检索 Tool。
 */
@Service
public class GaokaoUniversitySearchTool {

    private final GaokaoInputParser gaokaoInputParser;
    private final GaokaoKnowledgeService gaokaoKnowledgeService;
    private final ObjectMapper objectMapper;

    public GaokaoUniversitySearchTool(GaokaoInputParser gaokaoInputParser,
                                      GaokaoKnowledgeService gaokaoKnowledgeService,
                                      ObjectMapper objectMapper) {
        this.gaokaoInputParser = gaokaoInputParser;
        this.gaokaoKnowledgeService = gaokaoKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(RegistryAgentTool tool, String toolInputText) {
        GaokaoInputProfile profile = gaokaoInputParser.parse(toolInputText);
        List<GaokaoKnowledgeService.UniversityMatch> universities = gaokaoKnowledgeService.searchUniversities(profile, 5);
        StringBuilder builder = new StringBuilder("已执行高考院校检索。");
        if (universities.isEmpty()) {
            builder.append("\n当前未命中合适院校，建议补充省份、分数、位次、城市或专业偏好。");
        } else {
            for (int i = 0; i < universities.size(); i++) {
                GaokaoKnowledgeService.UniversityMatch item = universities.get(i);
                builder.append("\n").append(i + 1).append(". ")
                        .append(item.university().name())
                        .append("（").append(item.university().city()).append(" / ").append(item.university().tier()).append("）")
                        .append("\n   说明：").append(item.university().summary());
                if (item.admissionRule() != null) {
                    builder.append("\n   参考线：")
                            .append(item.admissionRule().province())
                            .append(" / ")
                            .append(item.admissionRule().subjectType())
                            .append(" / 参考分 ")
                            .append(item.admissionRule().referenceScore());
                }
                if (!item.reasons().isEmpty()) {
                    builder.append("\n   匹配原因：").append(String.join("；", item.reasons()));
                }
            }
        }
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已执行高考院校检索",
                builder.toString(),
                toJsonText(new UniversitySearchSnapshot(profile, universities))
        );
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("高考院校检索结果序列化失败");
        }
    }

    private record UniversitySearchSnapshot(
            GaokaoInputProfile profile,
            List<GaokaoKnowledgeService.UniversityMatch> universities
    ) {
    }
}
