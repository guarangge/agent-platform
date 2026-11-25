package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 任务记忆快照记录。
 *
 * @param taskMemorySnapshotId 任务记忆快照 ID
 * @param taskId               任务 ID
 * @param conversationId       会话 ID
 * @param memorySummary        任务记忆摘要
 * @param keyFindings          关键发现
 * @param finalResponseSummary 最终响应摘要
 * @param updatedAt            更新时间
 */
public record TaskMemorySnapshotRecord(
        Long taskMemorySnapshotId,
        Long taskId,
        Long conversationId,
        String memorySummary,
        String keyFindings,
        String finalResponseSummary,
        LocalDateTime updatedAt
) {
}
