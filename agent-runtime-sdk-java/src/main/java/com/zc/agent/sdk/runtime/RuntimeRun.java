package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Run 的稳定生命周期字段。
 *
 * @param runCode Run 编码
 * @param conversationId Thread ID
 * @param agentId Agent ID
 * @param status Run 状态
 * @param attemptCount 已执行次数
 * @param maxAttempts 最大执行次数
 * @param priority 运行优先级
 * @param taskId 关联 Task ID
 * @param errorMessage 错误信息
 * @param cancelRequested 是否已请求取消
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeRun(
        String runCode,
        Long conversationId,
        Long agentId,
        String status,
        Integer attemptCount,
        Integer maxAttempts,
        Integer priority,
        Long taskId,
        String errorMessage,
        Boolean cancelRequested
) {
}
