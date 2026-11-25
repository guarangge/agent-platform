package com.zc.agent.registry.modules.auth.vo;

import java.util.List;

/**
 * 当前登录用户响应。
 *
 * @param userId      用户 ID
 * @param username    登录用户名
 * @param displayName 用户显示名称
 * @param status      用户状态
 * @param roles       当前用户角色列表
 * @param menuScopes  当前用户可访问菜单范围
 * @param currentScope 当前生效作用域
 * @param availableScopes 可切换作用域
 */
public record CurrentUserResponse(
        Long userId,
        String username,
        String displayName,
        String status,
        List<RoleInfoResponse> roles,
        List<String> menuScopes,
        ScopeInfoResponse currentScope,
        List<ScopeInfoResponse> availableScopes
) {
}
