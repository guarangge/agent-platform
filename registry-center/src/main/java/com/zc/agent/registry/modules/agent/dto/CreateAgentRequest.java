package com.zc.agent.registry.modules.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 新增 Agent 请求。
 *
 * @param agentCode       Agent 编码
 * @param agentName       Agent 名称
 * @param description     描述
 * @param version         版本
 * @param defaultModel    默认模型
 * @param defaultPromptId 默认 Prompt ID
 * @param decisionPromptId 决策 Prompt ID
 * @param plannerPromptId 规划 Prompt ID
 * @param toolScopeMode   Tool 范围模式
 * @param status          状态
 * @param metadata        扩展元数据
 * @param remark          备注
 */
public record CreateAgentRequest(
        @NotBlank(message = "Agent 编码不能为空")
        @Size(max = 64, message = "Agent 编码长度不能超过64个字符")
        String agentCode,
        @NotBlank(message = "Agent 名称不能为空")
        @Size(max = 128, message = "Agent 名称长度不能超过128个字符")
        String agentName,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @NotBlank(message = "版本不能为空")
        @Size(max = 32, message = "版本长度不能超过32个字符")
        String version,
        @Size(max = 64, message = "默认模型长度不能超过64个字符")
        String defaultModel,
        @Positive(message = "默认 Prompt ID 必须为正整数")
        Long defaultPromptId,
        @Positive(message = "决策 Prompt ID 必须为正整数")
        Long decisionPromptId,
        @Positive(message = "规划 Prompt ID 必须为正整数")
        Long plannerPromptId,
        @NotBlank(message = "Tool 范围模式不能为空")
        @Size(max = 32, message = "Tool 范围模式长度不能超过32个字符")
        String toolScopeMode,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        JsonNode metadata,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
