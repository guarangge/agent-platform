package com.zc.agent.core.modules.runtime.capability.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Capability Policy 决策审计列表项。
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
 * @param decision 决策动作
 * @param requestedAction 原始策略动作
 * @param policyCode 策略编码
 * @param policyVersion 策略版本
 * @param runtimeCapabilities Runtime Capability 列表
 * @param sideEffectLevel 副作用等级
 * @param parameterHash 参数摘要
 * @param targetHash 目标摘要
 * @param approvalNodeId 审批节点 ID
 * @param approvalGranted 是否由审批授权续跑
 * @param occurredAt 决策时间
 */
public record CapabilityPolicyAuditItemResponse(
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
        List<String> runtimeCapabilities,
        String sideEffectLevel,
        String parameterHash,
        String targetHash,
        String approvalNodeId,
        boolean approvalGranted,
        LocalDateTime occurredAt
) {
}
