package com.zc.agent.registry.modules.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Agent 分页查询请求。
 *
 * @param pageNum   页码
 * @param pageSize  每页条数
 * @param agentCode Agent 编码
 * @param agentName Agent 名称
 * @param status    Agent 状态
 * @param version   Agent 版本
 */
public record AgentPageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "Agent 编码长度不能超过64个字符")
        String agentCode,
        @Size(max = 128, message = "Agent 名称长度不能超过128个字符")
        String agentName,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @Size(max = 32, message = "版本长度不能超过32个字符")
        String version
) {
}
