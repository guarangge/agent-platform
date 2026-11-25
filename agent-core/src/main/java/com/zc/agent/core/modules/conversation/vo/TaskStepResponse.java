package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * 任务步骤响应。
 *
 * @param taskStepId      步骤 ID
 * @param stepNo          步骤序号
 * @param stepType        步骤类型
 * @param stepName        步骤名称
 * @param status          状态
 * @param retryCount      重试次数
 * @param inputSnapshot   输入快照
 * @param outputSnapshot  输出快照
 * @param errorMessage    错误信息
 * @param startedAt       开始时间
 * @param finishedAt      结束时间
 * @param createdAt       创建时间
 */
public record TaskStepResponse(
        Long taskStepId,
        Integer stepNo,
        String stepType,
        String stepName,
        String status,
        Integer retryCount,
        String inputSnapshot,
        String outputSnapshot,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
