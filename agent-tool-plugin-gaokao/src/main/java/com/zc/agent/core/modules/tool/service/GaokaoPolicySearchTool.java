package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 高考政策检索 Tool。
 */
@Service
public class GaokaoPolicySearchTool {

    private final GaokaoInputParser gaokaoInputParser;
    private final GaokaoKnowledgeService gaokaoKnowledgeService;
    private final ObjectMapper objectMapper;

    public GaokaoPolicySearchTool(GaokaoInputParser gaokaoInputParser,
                                  GaokaoKnowledgeService gaokaoKnowledgeService,
                                  ObjectMapper objectMapper) {
        this.gaokaoInputParser = gaokaoInputParser;
        this.gaokaoKnowledgeService = gaokaoKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(RegistryAgentTool tool, String toolInputText) {
        GaokaoInputProfile profile = gaokaoInputParser.parse(toolInputText);
        List<GaokaoKnowledgeService.PolicyEntry> policies = gaokaoKnowledgeService.searchPolicies(profile, 3);
        StringBuilder builder = new StringBuilder("已执行高考政策检索。");
        if (policies.isEmpty()) {
            builder.append("\n当前未命中足够匹配的政策条目，建议补充省份、批次、平行志愿、调剂等更具体关键词。");
        } else {
            for (int i = 0; i < policies.size(); i++) {
                GaokaoKnowledgeService.PolicyEntry entry = policies.get(i);
                builder.append("\n").append(i + 1).append(". ").append(entry.title())
                        .append("（").append(entry.province()).append(" / ").append(entry.category()).append("）")
                        .append("\n   摘要：").append(entry.summary());
                if (entry.riskNotes() != null && !entry.riskNotes().isEmpty()) {
                    builder.append("\n   风险提示：").append(String.join("；", entry.riskNotes()));
                }
            }
            builder.append("\n请仍以目标省份当年考试院和招生院校正式发布文件为准。");
        }
        return new ToolExecutionResult(
                tool.toolCode(),
                tool.toolName(),
                "已执行高考政策检索",
                builder.toString(),
                toJsonText(new PolicySearchSnapshot(profile, policies))
        );
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("高考政策检索结果序列化失败");
        }
    }

    private record PolicySearchSnapshot(
            GaokaoInputProfile profile,
            List<GaokaoKnowledgeService.PolicyEntry> policies
    ) {
    }
}
