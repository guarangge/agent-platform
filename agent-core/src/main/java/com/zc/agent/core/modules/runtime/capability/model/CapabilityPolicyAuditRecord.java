package com.zc.agent.core.modules.runtime.capability.model;

import java.time.LocalDateTime;

/**
 * Capability Policy 决策审计数据库投影。
 *
 * @param eventId Runtime Event 主键
 * @param sequenceNo Run 内事件序号
 * @param runCode Run 编码
 * @param conversationId 会话 ID
 * @param conversationCode 会话编码
 * @param taskId Task ID
 * @param taskCode Task 编码
 * @param agentId Agent ID
 * @param agentName Agent 名称
 * @param traceId Trace ID
 * @param toolCallId Tool Call ID
 * @param toolCode Tool 编码
 * @param toolName Tool 名称
 * @param decision 最终决策
 * @param requestedAction 策略原始动作
 * @param policyCode 策略编码
 * @param policyVersion 策略版本
 * @param runtimeCapabilitiesJson Runtime Capability JSON
 * @param sideEffectLevel 副作用等级
 * @param parameterHash 参数摘要
 * @param targetHash 目标摘要
 * @param approvalNodeId 审批节点 ID
 * @param approvalGranted 是否已由审批授权
 * @param createdAt 决策时间
 */
public record CapabilityPolicyAuditRecord(
        Long eventId,
        Long sequenceNo,
        String runCode,
        Long conversationId,
        String conversationCode,
        Long taskId,
        String taskCode,
        Long agentId,
        String agentName,
        String traceId,
        String toolCallId,
        String toolCode,
        String toolName,
        String decision,
        String requestedAction,
        String policyCode,
        Integer policyVersion,
        String runtimeCapabilitiesJson,
        String sideEffectLevel,
        String parameterHash,
        String targetHash,
        String approvalNodeId,
        Boolean approvalGranted,
        LocalDateTime createdAt
) {
}
