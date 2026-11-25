package com.zc.agent.core.modules.runtime.kernel.interactive;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 单个 Interactive Loop 的内存预算状态机。
 * 所有消费方法原子判断后再记账，首次停止原因一旦确定便不可覆盖。
 */
public final class InteractiveLoopBudgetState {

    private final InteractiveLoopBudgetPolicy policy;
    private final Map<String, Integer> toolFingerprintCounts = new LinkedHashMap<>();
    private int iterations;
    private int llmCalls;
    private int toolCalls;
    private long tokens;
    private BigDecimal costAmount = BigDecimal.ZERO;
    private InteractiveLoopStopReason stopReason;

    public InteractiveLoopBudgetState(InteractiveLoopBudgetPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    /** 从持久化快照恢复预算计数；停止原因不会写入可恢复快照。 */
    public InteractiveLoopBudgetState(PersistentSnapshot snapshot) {
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        this.policy = java.util.Objects.requireNonNull(snapshot.policy(), "snapshot.policy");
        this.iterations = snapshot.iterations();
        this.llmCalls = snapshot.llmCalls();
        this.toolCalls = snapshot.toolCalls();
        this.tokens = snapshot.tokens();
        this.costAmount = snapshot.costAmount();
        this.toolFingerprintCounts.putAll(snapshot.toolFingerprintCounts());
    }

    /** 在开始新一轮 Observation/Action 前占用迭代额度。 */
    public synchronized ConsumptionResult consumeIteration() {
        if (isStopped()) return denied();
        if (iterations >= policy.maxIterations()) return stop(InteractiveLoopStopReason.MAX_ITERATIONS);
        iterations++;
        return allowed();
    }

    /** 在调用模型前按预计 Token 和成本原子占用额度。 */
    public synchronized ConsumptionResult consumeLlmCall(long projectedTokens, BigDecimal projectedCost) {
        validateUsage(projectedTokens, projectedCost);
        if (isStopped()) return denied();
        if (llmCalls >= policy.maxLlmCalls()) return stop(InteractiveLoopStopReason.MAX_LLM_CALLS);
        if (projectedTokens > policy.maxTokens() - tokens) return stop(InteractiveLoopStopReason.MAX_TOKENS);
        if (costAmount.add(projectedCost).compareTo(policy.maxCostAmount()) > 0) {
            return stop(InteractiveLoopStopReason.MAX_COST);
        }
        llmCalls++;
        tokens += projectedTokens;
        costAmount = costAmount.add(projectedCost);
        return allowed();
    }

    /** 在调用 Tool 前按 Tool 编码和规范化参数哈希占用调用及重复额度。 */
    public synchronized ConsumptionResult consumeToolCall(String toolCode, String parameterHash) {
        String fingerprint = fingerprint(toolCode, parameterHash);
        if (isStopped()) return denied();
        if (toolCalls >= policy.maxToolCalls()) return stop(InteractiveLoopStopReason.MAX_TOOL_CALLS);
        int repeated = toolFingerprintCounts.getOrDefault(fingerprint, 0);
        if (repeated >= policy.duplicateToolCallLimit()) {
            return stop(InteractiveLoopStopReason.DUPLICATE_TOOL_CALL);
        }
        toolCalls++;
        toolFingerprintCounts.put(fingerprint, repeated + 1);
        return allowed();
    }

    /** 由循环控制器写入成功、策略阻断、审批、取消或失败等显式停止原因。 */
    public synchronized ConsumptionResult stop(InteractiveLoopStopReason reason) {
        if (stopReason == null) {
            stopReason = java.util.Objects.requireNonNull(reason, "reason");
        }
        return denied();
    }

    /** 返回不包含 Tool 指纹的安全预算快照。 */
    public synchronized Snapshot snapshot() {
        return new Snapshot(iterations, llmCalls, toolCalls, tokens, costAmount,
                toolFingerprintCounts.size(), stopReason,
                policy.maxIterations(), policy.maxLlmCalls(), policy.maxToolCalls(),
                policy.maxTokens(), policy.maxCostAmount(), policy.duplicateToolCallLimit());
    }

    /** 返回仅供 Checkpoint 恢复使用的完整状态，Tool 参数只保留不可逆指纹。 */
    public synchronized PersistentSnapshot persistentSnapshot() {
        return new PersistentSnapshot(policy, iterations, llmCalls, toolCalls, tokens, costAmount,
                Map.copyOf(toolFingerprintCounts));
    }

    private boolean isStopped() {
        return stopReason != null;
    }

    private ConsumptionResult allowed() {
        return new ConsumptionResult(true, null, snapshot());
    }

    private ConsumptionResult denied() {
        return new ConsumptionResult(false, stopReason, snapshot());
    }

    private void validateUsage(long projectedTokens, BigDecimal projectedCost) {
        if (projectedTokens < 0 || projectedCost == null || projectedCost.signum() < 0) {
            throw new IllegalArgumentException("预计 Token 和成本不能为负数");
        }
    }

    private String fingerprint(String toolCode, String parameterHash) {
        String normalizedToolCode = toolCode == null ? "" : toolCode.trim();
        String normalizedHash = parameterHash == null ? "" : parameterHash.trim().toLowerCase(Locale.ROOT);
        if (normalizedToolCode.isBlank() || normalizedHash.isBlank()) {
            throw new IllegalArgumentException("Tool 编码和参数哈希不能为空");
        }
        return normalizedToolCode + ":" + normalizedHash;
    }

    /**
     * @param allowed 是否已成功消费本次额度
     * @param stopReason 停止原因；允许继续时为空
     * @param snapshot 消费后的安全快照
     */
    public record ConsumptionResult(boolean allowed,
                                    InteractiveLoopStopReason stopReason,
                                    Snapshot snapshot) {
    }

    /** 预算状态安全快照，不包含 Tool 参数或调用指纹。 */
    public record Snapshot(int iterations,
                           int llmCalls,
                           int toolCalls,
                           long tokens,
                           BigDecimal costAmount,
                           int uniqueToolFingerprints,
                           InteractiveLoopStopReason stopReason,
                           int maxIterations,
                           int maxLlmCalls,
                           int maxToolCalls,
                           long maxTokens,
                           BigDecimal maxCostAmount,
                           int duplicateToolCallLimit) {
    }


    /** Interactive Loop 可恢复预算状态。 */
    public record PersistentSnapshot(InteractiveLoopBudgetPolicy policy,
                                     int iterations,
                                     int llmCalls,
                                     int toolCalls,
                                     long tokens,
                                     BigDecimal costAmount,
                                     Map<String, Integer> toolFingerprintCounts) {
        public PersistentSnapshot {
            if (policy == null || iterations < 0 || llmCalls < 0 || toolCalls < 0 || tokens < 0
                    || costAmount == null || costAmount.signum() < 0) {
                throw new IllegalArgumentException("Interactive Loop 持久化预算快照非法");
            }
            toolFingerprintCounts = toolFingerprintCounts == null
                    ? Map.of() : Map.copyOf(toolFingerprintCounts);
            if (toolFingerprintCounts.values().stream().anyMatch(value -> value == null || value < 1)) {
                throw new IllegalArgumentException("Interactive Loop Tool 指纹计数非法");
            }
        }
    }
}
