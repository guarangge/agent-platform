package com.zc.agent.core.modules.runtime.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将历史计划的真实执行结果转换为 Planner 可消费的结构化反馈。
 */
@Service
public class PlanOutcomeFeedbackService {

    /**
     * 聚合成功模式、失败模式和下一次规划约束，避免 Planner 重复历史错误。
     */
    public Map<String, Object> summarize(List<ExecutionPlannerService.PlanTemplateHint> hints) {
        List<ExecutionPlannerService.PlanTemplateHint> safeHints = hints == null ? List.of() : hints;
        List<Map<String, Object>> successes = safeHints.stream()
                .filter(item -> "success".equalsIgnoreCase(item.finalStatus()))
                .limit(3)
                .map(this::toOutcome)
                .toList();
        List<Map<String, Object>> failures = safeHints.stream()
                .filter(item -> !item.finalStatus().isBlank() && !"success".equalsIgnoreCase(item.finalStatus()))
                .limit(3)
                .map(this::toOutcome)
                .toList();
        List<String> recommendations = failures.stream()
                .map(item -> "避免重复失败计划 " + item.get("planId") + "：" + item.get("errorMessage"))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successfulPatterns", successes);
        result.put("failedPatterns", failures);
        result.put("recommendations", recommendations);
        result.put("sampleCount", safeHints.size());
        return result;
    }

    private Map<String, Object> toOutcome(ExecutionPlannerService.PlanTemplateHint item) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("planId", item.planId());
        outcome.put("planType", item.planType());
        outcome.put("goal", item.goal());
        outcome.put("qualityScore", item.qualityScore());
        outcome.put("finalStatus", item.finalStatus());
        outcome.put("errorMessage", item.errorMessage());
        outcome.put("stepOutline", item.stepOutline());
        return outcome;
    }
}
