package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;

/**
 * Agent 异步运行响应。
 *
 * @param runCode 运行编码
 * @param conversationId 会话 ID
 * @param agentId Agent ID
 * @param status 运行状态
 * @param attemptCount 已执行次数
 * @param maxAttempts 最大执行次数
 * @param priority 持久化优先级
 * @param taskId 关联任务 ID
 * @param errorMessage 错误信息
 * @param cancelRequested 是否已请求取消
 * @param availableAt 下次可领取时间
 * @param createdAt 创建时间
 * @param startedAt 开始时间
 * @param finishedAt 结束时间
 * @param workerGroupCode 目标 Worker Group
 * @param requiredCapabilitiesJson 所需能力标签 JSON
 */
public record AgentRunResponse(
        String runCode,
        Long conversationId,
        Long agentId,
        String status,
        Integer attemptCount,
        Integer maxAttempts,
        Integer priority,
        Long taskId,
        String errorMessage,
        Boolean cancelRequested,
        LocalDateTime availableAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String workerGroupCode,
        String requiredCapabilitiesJson
) {
}
