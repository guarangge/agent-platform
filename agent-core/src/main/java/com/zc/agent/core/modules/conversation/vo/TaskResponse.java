package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务响应。
 *
 * @param taskId         任务 ID
 * @param taskCode       任务编码
 * @param conversationId 会话 ID
 * @param messageId      用户消息 ID
 * @param agentId        Agent ID
 * @param parentTaskId   父任务 ID
 * @param rootTaskId     根任务 ID
 * @param triggerTaskStepId 触发当前任务的父步骤 ID
 * @param taskKind       任务类型
 * @param executionType  执行分类
 * @param currentPlanVersion 当前计划版本
 * @param executionChainJson 调用链路快照
 * @param traceId        追踪ID
 * @param promptId       Prompt ID
 * @param promptName     Prompt 名称
 * @param status         状态
 * @param startedAt      开始时间
 * @param finishedAt     结束时间
 * @param errorMessage   错误信息
 * @param memorySummary  执行级记忆摘要
 * @param keyFindings    关键发现
 * @param finalResponseSummary 最终响应摘要
 * @param createdAt      创建时间
 * @param approvals      审批记录
 * @param plans          计划版本列表
 * @param traces         追踪事件列表
 * @param steps          步骤列表
 */
public record TaskResponse(
        Long taskId,
        String taskCode,
        Long conversationId,
        Long messageId,
        Long agentId,
        Long parentTaskId,
        Long rootTaskId,
        Long triggerTaskStepId,
        String taskKind,
        String executionType,
        Integer currentPlanVersion,
        String executionChainJson,
        String traceId,
        Long promptId,
        String promptName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage,
        String memorySummary,
        String keyFindings,
        String finalResponseSummary,
        LocalDateTime createdAt,
        List<ApprovalResponse> approvals,
        List<TaskPlanResponse> plans,
        List<TaskTraceResponse> traces,
        List<TaskStepResponse> steps
) {
}
