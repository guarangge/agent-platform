package com.zc.agent.core.modules.cost.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cost 治理写入请求。 */
public final class CostGovernanceRequests {
    private CostGovernanceRequests() { }

    /** 资源价格保存请求。 */
    public record PriceRequest(@NotBlank String resourceType, @NotBlank String resourceCode,
                               @NotNull @DecimalMin("0") BigDecimal inputPricePer1000,
                               @NotNull @DecimalMin("0") BigDecimal outputPricePer1000,
                               @NotNull @DecimalMin("0") BigDecimal unitPrice,
                               @NotBlank String currency, @NotBlank String status,
                               LocalDateTime effectiveFrom, LocalDateTime effectiveTo) { }

    /** 预算策略保存请求。 */
    public record BudgetPolicyRequest(@NotBlank String policyCode, @NotBlank String policyName,
                                      @NotBlank String scopeType, @NotNull Long scopeId,
                                      @NotBlank String periodType, @DecimalMin("0") BigDecimal budgetAmount,
                                      @Min(0) Long llmTokenLimit, @Min(0) Long toolCallLimit,
                                      @Min(0) Long sandboxSecondLimit, @Min(0) Long delegationCallLimit,
                                      @NotNull @DecimalMin("0.01") @Max(100) BigDecimal warningPercent,
                                      @NotNull Boolean hardLimit, @NotBlank String currency,
                                      @NotNull Integer priority, @NotBlank String status,
                                      LocalDateTime effectiveFrom, LocalDateTime effectiveTo) { }

    /** 告警状态更新请求。 */
    public record AlertStatusRequest(@NotBlank String status) { }
}
