package com.zc.agent.registry.modules.role.model;

import java.time.LocalDateTime;

/**
 * 角色分页数据。
 *
 * @param roleId      角色 ID
 * @param roleCode    角色编码
 * @param roleName    角色名称
 * @param status      角色状态
 * @param description 角色描述
 * @param createdAt   创建时间
 */
public record RolePageRecord(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        String description,
        LocalDateTime createdAt
) {
}
