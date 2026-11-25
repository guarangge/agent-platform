package com.zc.agent.registry.modules.agent.model;

import java.time.LocalDateTime;

/**
 * Agent 详情记录。
 *
 * @param agentId          Agent ID
 * @param agentCode        Agent 编码
 * @param agentName        Agent 名称
 * @param description      描述
 * @param version          版本
 * @param defaultModel     默认模型
 * @param defaultPromptId  默认 Prompt ID
 * @param defaultPromptName 默认 Prompt 名称
 * @param decisionPromptId  决策 Prompt ID
 * @param decisionPromptName 决策 Prompt 名称
 * @param plannerPromptId  规划 Prompt ID
 * @param plannerPromptName 规划 Prompt 名称
 * @param toolScopeMode    Tool 范围模式
 * @param status           状态
 * @param metadataJsonText 元数据 JSON
 * @param remark           备注
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param createdBy        创建人
 * @param updatedBy        更新人
 */
public record AgentDetailRecord(
        Long agentId,
        String agentCode,
        String agentName,
        String description,
        String version,
        String defaultModel,
        Long defaultPromptId,
        String defaultPromptName,
        Long decisionPromptId,
        String decisionPromptName,
        Long plannerPromptId,
        String plannerPromptName,
        String toolScopeMode,
        String status,
        String metadataJsonText,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
