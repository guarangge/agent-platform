package com.zc.agent.core.modules.runtime.capability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * Capability Policy 决策审计查询条件。
 *
 * @param pageNum 当前页码
 * @param pageSize 每页条数
 * @param decision 决策动作
 * @param toolCode Tool 编码
 * @param capability Runtime Capability
 * @param sideEffectLevel 副作用等级
 * @param policyCode 策略编码
 * @param agentId Agent ID
 * @param runCode Run 编码
 * @param traceId Trace ID
 * @param occurredFrom 发生时间下界
 * @param occurredTo 发生时间上界
 */
public record CapabilityPolicyAuditQueryRequest(
        @Min(1) Integer pageNum,
        @Min(1) @Max(200) Integer pageSize,
        String decision,
        String toolCode,
        String capability,
        String sideEffectLevel,
        String policyCode,
        Long agentId,
        String runCode,
        String traceId,
        LocalDateTime occurredFrom,
        LocalDateTime occurredTo
) {
}
