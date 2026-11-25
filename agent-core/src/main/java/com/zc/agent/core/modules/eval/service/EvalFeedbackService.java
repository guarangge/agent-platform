package com.zc.agent.core.modules.eval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.eval.model.EvalRunRecord;
import com.zc.agent.core.modules.eval.repository.EvalRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eval 反馈反哺服务。
 */
@Service
public class EvalFeedbackService {

    private static final int DEFAULT_TOOL_SAMPLE_LIMIT = 20;

    private final EvalRepository evalRepository;
    private final ObjectMapper objectMapper;

    public EvalFeedbackService(EvalRepository evalRepository,
                               ObjectMapper objectMapper) {
        this.evalRepository = evalRepository;
        this.objectMapper = objectMapper;
    }

    public EvalFeedbackSummary summarizeAgentFeedback(Long agentId) {
        if (agentId == null) {
            return EvalFeedbackSummary.empty();
        }
        EvalRunRecord latestRun = evalRepository.findLatestCompletedRunByAgent(agentId).orElse(null);
        List<String> preferredToolCodes = normalizeToolCodes(
                evalRepository.findRecentSuccessfulToolCodesByAgent(agentId, DEFAULT_TOOL_SAMPLE_LIMIT)
        );
        if (latestRun == null) {
            return new EvalFeedbackSummary(null, null, null, List.copyOf(preferredToolCodes));
        }
        BigDecimal passRate = latestRun.totalCases() == null || latestRun.totalCases() == 0
                ? null
                : BigDecimal.valueOf(latestRun.passedCases() * 100D / latestRun.totalCases()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new EvalFeedbackSummary(
                latestRun.averageScore(),
                passRate,
                latestRun.runCode(),
                List.copyOf(preferredToolCodes)
        );
    }

    private List<String> normalizeToolCodes(List<String> rawValues) {
        Set<String> toolCodes = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            List<String> parsedCodes = parseToolCodes(rawValue);
            if (!parsedCodes.isEmpty()) {
                toolCodes.addAll(parsedCodes);
                continue;
            }
            String[] parts = rawValue.split("[,，]");
            for (String part : parts) {
                String toolCode = part.trim();
                if (!toolCode.isBlank()) {
                    toolCodes.add(toolCode);
                }
            }
        }
        return new ArrayList<>(toolCodes);
    }

    private List<String> parseToolCodes(String rawValue) {
        try {
            List<String> values = objectMapper.readValue(rawValue, new TypeReference<List<String>>() {
            });
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    public record EvalFeedbackSummary(
            BigDecimal latestAverageScore,
            BigDecimal latestPassRate,
            String latestRunCode,
            List<String> preferredToolCodes
    ) {
        public static EvalFeedbackSummary empty() {
            return new EvalFeedbackSummary(null, null, null, List.of());
        }
    }
}
