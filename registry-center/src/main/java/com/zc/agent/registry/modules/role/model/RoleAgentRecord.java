package com.zc.agent.registry.modules.role.model;

import java.time.LocalDateTime;

/**
 * 角色已绑定 Agent 记录。
 *
 * @param agentId       Agent ID
 * @param agentCode     Agent 编码
 * @param agentName     Agent 名称
 * @param status        Agent 状态
 * @param version       Agent 版本
 * @param description   Agent 描述
 * @param roleBoundAt   角色绑定时间
 */
public record RoleAgentRecord(
        Long agentId,
        String agentCode,
        String agentName,
        String status,
        String version,
        String description,
        LocalDateTime roleBoundAt
) {
}
