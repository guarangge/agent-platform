package com.zc.agent.registry.modules.role.model;

import java.time.LocalDateTime;

/**
 * 角色详情数据。
 *
 * @param roleId        角色 ID
 * @param roleCode      角色编码
 * @param roleName      角色名称
 * @param status        角色状态
 * @param menuScopeJson 菜单范围 JSON
 * @param description   角色描述
 * @param remark        备注
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param createdBy     创建人
 * @param updatedBy     更新人
 */
public record RoleDetailRecord(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        String menuScopeJson,
        String description,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
