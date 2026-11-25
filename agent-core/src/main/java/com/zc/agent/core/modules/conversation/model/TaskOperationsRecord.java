package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 任务运行中心查询记录。
 *
 * @param taskId              任务 ID
 * @param taskCode            任务编码
 * @param runCode             异步运行编码
 * @param conversationId      会话 ID
 * @param conversationCode    会话编码
 * @param conversationTitle   会话标题
 * @param agentId             Agent ID
 * @param agentName           Agent 名称
 * @param parentTaskId        父任务 ID
 * @param rootTaskId          根任务 ID
 * @param taskKind            任务类型
 * @param executionType       执行类型
 * @param traceId             Trace ID
 * @param currentPlanVersion  当前计划版本
 * @param status              任务状态
 * @param errorMessage        错误信息
 * @param stepCount           步骤数
 * @param successStepCount    成功步骤数
 * @param failedStepCount     失败步骤数
 * @param approvalCount       审批数
 * @param pendingApprovalCount 待审批数
 * @param delegationCount     直接委派数
 * @param startedAt           开始时间
 * @param finishedAt          结束时间
 * @param createdAt           创建时间
 */
public record TaskOperationsRecord(
        Long taskId,
        String taskCode,
        String runCode,
        Long conversationId,
        String conversationCode,
        String conversationTitle,
        Long agentId,
        String agentName,
        Long parentTaskId,
        Long rootTaskId,
        String taskKind,
        String executionType,
        String traceId,
        Integer currentPlanVersion,
        String status,
        String errorMessage,
        Integer stepCount,
        Integer successStepCount,
        Integer failedStepCount,
        Integer approvalCount,
        Integer pendingApprovalCount,
        Integer delegationCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
