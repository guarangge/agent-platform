package com.zc.agent.registry.modules.policy.dto;

/**
 * Guardrail 策略分页查询请求。
 *
 * @param pageNum    页码
 * @param pageSize   每页条数
 * @param policyCode 策略编码
 * @param policyName 策略名称
 * @param status     状态
 * @param actionType 动作类型
 */
public record GuardrailPolicyPageQueryRequest(
        Integer pageNum,
        Integer pageSize,
        String policyCode,
        String policyName,
        String status,
        String actionType
) {
}
