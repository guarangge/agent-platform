package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * 任务委派记录。
 */
public record TaskDelegationRecord(
        Long delegationId,
        Long parentTaskId,
        Long parentTaskStepId,
        Long childTaskId,
        Long conversationId,
        Long delegateAgentId,
        String delegateAgentCode,
        String delegateAgentName,
        String delegateInstruction,
        String traceId,
        String status,
        String inputSnapshot,
        String outputSnapshot,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
