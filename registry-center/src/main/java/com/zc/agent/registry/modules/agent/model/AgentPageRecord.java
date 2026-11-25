package com.zc.agent.registry.modules.agent.model;

import java.time.LocalDateTime;

/**
 * Agent 分页记录。
 *
 * @param agentId      Agent ID
 * @param agentCode    Agent 编码
 * @param agentName    Agent 名称
 * @param description  描述
 * @param version      版本
 * @param defaultModel 默认模型
 * @param status       状态
 * @param createdAt    创建时间
 */
public record AgentPageRecord(
        Long agentId,
        String agentCode,
        String agentName,
        String description,
        String version,
        String defaultModel,
        String status,
        LocalDateTime createdAt
) {
}
