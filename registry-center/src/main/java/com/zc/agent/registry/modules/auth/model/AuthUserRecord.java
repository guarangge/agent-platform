package com.zc.agent.registry.modules.auth.model;

/**
 * 用户查询结果。
 *
 * @param userId       用户 ID
 * @param username     登录用户名
 * @param passwordHash 密码哈希
 * @param displayName  用户显示名称
 * @param status       用户状态
 */
public record AuthUserRecord(
        Long userId,
        String username,
        String passwordHash,
        String displayName,
        String status
) {
}
