package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 用户记忆记录。
 *
 * @param userMemoryId       用户记忆 ID
 * @param userId             用户 ID
 * @param agentId            Agent ID
 * @param preferenceSummary  用户偏好摘要
 * @param attentionSummary   用户关注点摘要
 * @param factSummary        用户长期事实摘要
 * @param goalSummary        用户长期目标摘要
 * @param updatedAt          更新时间
 */
public record UserMemoryRecord(
        Long userMemoryId,
        Long userId,
        Long agentId,
        String preferenceSummary,
        String attentionSummary,
        String factSummary,
        String goalSummary,
        LocalDateTime updatedAt
) {
}
