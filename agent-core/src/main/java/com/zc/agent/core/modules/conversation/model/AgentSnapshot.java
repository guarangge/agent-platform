package com.zc.agent.core.modules.conversation.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 快照。
 *
 * @param agentId            Agent ID
 * @param agentCode          Agent 编码
 * @param agentName          Agent 名称
 * @param defaultModel       默认模型
 * @param defaultPromptId    默认 Prompt ID
 * @param defaultPromptName  默认 Prompt 名称
 * @param decisionPromptId   决策 Prompt ID
 * @param decisionPromptName 决策 Prompt 名称
 * @param plannerPromptId    规划 Prompt ID
 * @param plannerPromptName  规划 Prompt 名称
 * @param status             Agent 状态
 * @param metadata           Agent 元数据
 */
public record AgentSnapshot(
        Long agentId,
        String agentCode,
        String agentName,
        String defaultModel,
        Long defaultPromptId,
        String defaultPromptName,
        Long decisionPromptId,
        String decisionPromptName,
        Long plannerPromptId,
        String plannerPromptName,
        String status,
        JsonNode metadata
) {
}
