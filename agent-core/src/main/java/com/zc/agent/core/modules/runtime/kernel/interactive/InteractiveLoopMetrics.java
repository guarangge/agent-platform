package com.zc.agent.core.modules.runtime.kernel.interactive;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 汇总 Interactive Loop 灰度与停止指标。
 * Tag 只使用有限枚举，避免把 Agent、Tool 或用户输入写入监控系统。
 */
@Service
public class InteractiveLoopMetrics {

    private static final Set<String> ROUTE_REASONS = Set.of(
            "interactive_loop_kill_switched",
            "interactive_loop_disabled",
            "interactive_loop_project_not_allowlisted",
            "interactive_loop_agent_allowlisted",
            "interactive_loop_agent_not_allowlisted",
            "interactive_loop_full_rollout",
            "interactive_loop_percentage_rollout",
            "interactive_loop_rollout_excluded"
    );
    private final MeterRegistry meterRegistry;
    private final DistributionSummary iterations;
    private final DistributionSummary llmCalls;
    private final DistributionSummary toolCalls;
    private final DistributionSummary tokens;
    private final DistributionSummary costAmount;

    public InteractiveLoopMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.iterations = summary("agent.runtime.interactive.loop.iterations", "iterations");
        this.llmCalls = summary("agent.runtime.interactive.loop.llm.calls", "calls");
        this.toolCalls = summary("agent.runtime.interactive.loop.tool.calls", "calls");
        this.tokens = summary("agent.runtime.interactive.loop.tokens", "tokens");
        this.costAmount = summary("agent.runtime.interactive.loop.cost.amount", "currency");
    }

    /** 记录一次 Interactive Loop 路由选择。 */
    public void recordRoute(boolean enabled, String reason) {
        meterRegistry.counter(
                "agent.runtime.interactive.loop.route",
                "outcome", enabled ? "selected" : "fallback",
                "reason", normalize(reason)
        ).increment();
    }

    /** 记录一次 Loop 停止及其安全预算快照。 */
    public void recordStopped(InteractiveLoopBudgetState.Snapshot snapshot) {
        String reason = snapshot.stopReason().code();
        meterRegistry.counter("agent.runtime.interactive.loop.stop", "reason", reason).increment();
        iterations.record(snapshot.iterations());
        llmCalls.record(snapshot.llmCalls());
        toolCalls.record(snapshot.toolCalls());
        tokens.record(snapshot.tokens());
        costAmount.record(snapshot.costAmount().doubleValue());
    }

    private DistributionSummary summary(String name, String baseUnit) {
        return DistributionSummary.builder(name)
                .baseUnit(baseUnit)
                .register(meterRegistry);
    }

    private String normalize(String reason) {
        return ROUTE_REASONS.contains(reason) ? reason : "unknown";
    }
}
