package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 任务记录。
 *
 * @param taskId         任务 ID
 * @param taskCode       任务编码
 * @param traceId        追踪 ID
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
 * @param promptId       Prompt ID
 * @param promptName     Prompt 名称
 * @param status         状态
 * @param startedAt      开始时间
 * @param finishedAt     结束时间
 * @param errorMessage   错误信息
 * @param createdAt      创建时间
 */
public record TaskRecord(
        Long taskId,
        String taskCode,
        String traceId,
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
        Long promptId,
        String promptName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage,
        LocalDateTime createdAt
) {
}
