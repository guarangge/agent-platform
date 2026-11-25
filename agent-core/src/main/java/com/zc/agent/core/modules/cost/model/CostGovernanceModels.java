package com.zc.agent.core.modules.cost.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cost 治理模块使用的稳定查询模型。 */
public final class CostGovernanceModels {
    private CostGovernanceModels() { }

    /** 资源价格。 */
    public record ResourcePrice(Long id, String resourceType, String resourceCode,
                                BigDecimal inputPricePer1000, BigDecimal outputPricePer1000,
                                BigDecimal unitPrice, String currency, String status,
                                LocalDateTime effectiveFrom, LocalDateTime effectiveTo) { }

    /** 预算策略。 */
    public record BudgetPolicy(Long id, String policyCode, String policyName, String scopeType, Long scopeId,
                               String periodType, BigDecimal budgetAmount, Long llmTokenLimit,
                               Long toolCallLimit, Long sandboxSecondLimit, Long delegationCallLimit,
                               BigDecimal warningPercent, boolean hardLimit, String currency,
                               Integer priority, String status, LocalDateTime effectiveFrom,
                               LocalDateTime effectiveTo) { }

    /** 当前周期汇总。 */
    public record UsageSummary(BigDecimal amount, long llmTokens, long toolCalls,
                               BigDecimal sandboxSeconds, long delegationCalls, long eventCount,
                               long unpricedEvents) { }

    /** 用量流水。 */
    public record UsageEvent(Long id, String eventCode, String resourceType, String resourceCode,
                             String provider, Long userId, Long agentId, Long conversationId, Long taskId,
                             long inputUnits, long outputUnits, BigDecimal quantity, String unit,
                             BigDecimal amount, String currency, String pricingStatus,
                             String executionStatus, Long durationMs, LocalDateTime occurredAt) { }

    /** 预算告警。 */
    public record BudgetAlert(Long id, Long policyId, String policyCode, String policyName,
                              String alertType, String metricType, BigDecimal currentValue,
                              BigDecimal limitValue, BigDecimal usagePercent, String status,
                              String message, LocalDateTime periodStart, LocalDateTime periodEnd,
                              LocalDateTime triggeredAt, LocalDateTime resolvedAt) { }
}
