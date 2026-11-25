package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 高考专业检索 Tool。
 */
@Service
public class GaokaoMajorSearchTool {

    private final GaokaoInputParser gaokaoInputParser;
    private final GaokaoKnowledgeService gaokaoKnowledgeService;
    private final ObjectMapper objectMapper;

    public GaokaoMajorSearchTool(GaokaoInputParser gaokaoInputParser,
                                 GaokaoKnowledgeService gaokaoKnowledgeService,
                                 ObjectMapper objectMapper) {
        this.gaokaoInputParser = gaokaoInputParser;
        this.gaokaoKnowledgeService = gaokaoKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(RegistryAgentTool tool, String toolInputText) {
        GaokaoInputProfile profile = gaokaoInputParser.parse(toolInputText);
        List<GaokaoKnowledgeService.MajorMatch> majors = gaokaoKnowledgeService.searchMajors(profile, 5);
        StringBuilder builder = new StringBuilder("已执行高考专业检索。");
        if (majors.isEmpty()) {
            builder.append("\n当前未命中合适专业，建议补充目标方向、选科、分数或就业诉求。");
        } else {
            for (int i = 0; i < majors.size(); i++) {
                GaokaoKnowledgeService.MajorMatch item = majors.get(i);
                builder.append("\n").append(i + 1).append(". ").append(item.major().name())
                        .append("（").append(item.major().category()).append("）")
                        .append("\n   说明：").append(item.major().description());
                if (item.major().careerDirections() != null && !item.major().careerDirections().isEmpty()) {
                    builder.append("\n   就业方向：").append(String.join("、", item.major().careerDirections()));
                }
                if (!item.reasons().isEmpty()) {
                    builder.append("\n   匹配原因：").append(String.join("；", item.reasons()));
                }
            }
        }
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已执行高考专业检索",
                builder.toString(),
                toJsonText(new MajorSearchSnapshot(profile, majors))
        );
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("高考专业检索结果序列化失败");
        }
    }

    private record MajorSearchSnapshot(
            GaokaoInputProfile profile,
            List<GaokaoKnowledgeService.MajorMatch> majors
    ) {
    }
}
