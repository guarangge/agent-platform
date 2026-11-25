package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 任务追踪记录。
 *
 * @param taskTraceId    追踪记录ID
 * @param taskId         任务ID
 * @param traceId        追踪ID
 * @param taskStepId     关联步骤ID
 * @param eventType      事件类型
 * @param spanName       追踪名称
 * @param traceStatus    追踪状态
 * @param inputSnapshot  输入快照
 * @param outputSnapshot 输出快照
 * @param errorMessage   错误信息
 * @param startedAt      开始时间
 * @param finishedAt     结束时间
 * @param durationMs     耗时毫秒
 * @param metadata       扩展元数据
 * @param createdAt      创建时间
 */
public record TaskTraceRecord(
        Long taskTraceId,
        Long taskId,
        String traceId,
        Long taskStepId,
        String eventType,
        String spanName,
        String traceStatus,
        String inputSnapshot,
        String outputSnapshot,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        String metadata,
        LocalDateTime createdAt
) {
}
