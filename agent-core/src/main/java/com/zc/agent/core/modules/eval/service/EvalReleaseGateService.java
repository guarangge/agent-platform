package com.zc.agent.core.modules.eval.service;

import com.zc.agent.core.modules.eval.model.EvalRunRecord;
import com.zc.agent.core.modules.eval.repository.EvalRepository;
import com.zc.agent.core.modules.eval.vo.AgentReleaseGateResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 基于 Agent 最新完成 Eval Run 执行发布门禁判定。 */
@Service
public class EvalReleaseGateService {
    private final EvalRepository evalRepository;

    public EvalReleaseGateService(EvalRepository evalRepository) {
        this.evalRepository = evalRepository;
    }

    /** 使用调用方发布策略判定 Agent 是否允许启用。 */
    public AgentReleaseGateResponse evaluate(Long agentId,
                                             BigDecimal minimumAverageScore,
                                             BigDecimal minimumPassRate,
                                             Integer minimumCases,
                                             Integer maximumAgeHours) {
        EvalRunRecord run = evalRepository.findLatestCompletedRunByAgent(agentId).orElse(null);
        if (run == null) {
            return new AgentReleaseGateResponse(agentId, false, List.of("不存在已完成的 Eval Run"),
                    null, null, null, null, null, null);
        }
        int passedCases = run.passedCases() == null ? 0 : run.passedCases();
        BigDecimal passRate = run.totalCases() == null || run.totalCases() == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(passedCases * 100D / run.totalCases()).setScale(2, RoundingMode.HALF_UP);
        List<String> reasons = new ArrayList<>();
        if (minimumCases != null && run.totalCases() < minimumCases) {
            reasons.add("Eval 样本数低于 " + minimumCases);
        }
        if (minimumAverageScore != null && (run.averageScore() == null
                || run.averageScore().compareTo(minimumAverageScore) < 0)) {
            reasons.add("Eval 平均分低于 " + minimumAverageScore);
        }
        if (minimumPassRate != null && passRate.compareTo(minimumPassRate) < 0) {
            reasons.add("Eval 通过率低于 " + minimumPassRate + "%");
        }
        if (maximumAgeHours != null && (run.finishedAt() == null
                || run.finishedAt().isBefore(LocalDateTime.now().minusHours(maximumAgeHours)))) {
            reasons.add("Eval 报告已超过 " + maximumAgeHours + " 小时");
        }
        if (!"success".equals(run.runStatus())) {
            reasons.add("最新 Eval Run 状态不是 success");
        }
        return new AgentReleaseGateResponse(agentId, reasons.isEmpty(), List.copyOf(reasons), run.runId(),
                run.runCode(), run.totalCases(), run.averageScore(), passRate, run.finishedAt());
    }
}
