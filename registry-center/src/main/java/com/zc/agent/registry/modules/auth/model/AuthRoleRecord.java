package com.zc.agent.registry.modules.auth.model;

/**
 * 角色查询结果。
 *
 * @param roleId        角色 ID
 * @param roleCode      角色编码
 * @param roleName      角色名称
 * @param menuScopeJson 角色菜单范围 JSON 字符串
 */
public record AuthRoleRecord(
        Long roleId,
        String roleCode,
        String roleName,
        String menuScopeJson
) {
}
