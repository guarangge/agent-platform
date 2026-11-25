package com.zc.agent.core.security;

import java.util.List;

/**
 * 当前登录用户安全上下文对象。
 */
public record SecurityUser(
        Long userId,
        String username,
        String displayName,
        List<String> roleCodes,
        List<String> menuScopes,
        Long tenantId,
        Long organizationId,
        Long workspaceId,
        Long projectId
) {
    public SecurityUser(Long userId, String username, String displayName,
                        List<String> roleCodes, List<String> menuScopes) {
        this(userId, username, displayName, roleCodes, menuScopes, 1L, 1L, 1L, 1L);
    }
}
