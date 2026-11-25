package com.zc.agent.core.modules.runtime.capability.model;

import java.util.List;
import java.util.Map;

/**
 * Tool 调用前的 Capability Policy 决策。
 *
 * @param action 决策动作：allow/sandbox/approval/block
 * @param policyCode 主策略编码
 * @param policyName 主策略名称
 * @param policyVersion 主策略版本
 * @param reason 决策原因
 * @param matchedCapabilities 命中的运行时能力
 * @param sideEffectLevel Tool 副作用等级
 * @param parameterHash 参数摘要哈希
 * @param targetHash 目标资源摘要哈希
 * @param approvalNodeId 稳定审批节点 ID
 * @param approvalGranted 是否由当前已批准审批授予
 * @param metadata 可审计的安全决策元数据
 */
public record CapabilityPolicyDecision(
        String action,
        String policyCode,
        String policyName,
        Integer policyVersion,
        String reason,
        List<String> matchedCapabilities,
        String sideEffectLevel,
        String parameterHash,
        String targetHash,
        String approvalNodeId,
        boolean approvalGranted,
        Map<String, Object> metadata
) {
}
