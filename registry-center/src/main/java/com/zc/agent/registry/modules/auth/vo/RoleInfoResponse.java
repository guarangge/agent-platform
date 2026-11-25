package com.zc.agent.registry.modules.auth.vo;

/**
 * 当前用户角色信息。
 *
 * @param roleId   角色 ID
 * @param roleCode 角色编码
 * @param roleName 角色名称
 */
public record RoleInfoResponse(
        Long roleId,
        String roleCode,
        String roleName
) {
}
