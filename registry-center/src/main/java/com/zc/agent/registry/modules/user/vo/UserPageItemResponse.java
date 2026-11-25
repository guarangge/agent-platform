package com.zc.agent.registry.modules.user.vo;

import java.time.LocalDateTime;

/**
 * 用户分页项响应。
 *
 * @param userId      用户 ID
 * @param username    登录账号
 * @param displayName 显示名称
 * @param email       邮箱
 * @param mobile      手机号
 * @param status      用户状态
 * @param lastLoginAt 最近登录时间
 * @param createdAt   创建时间
 */
public record UserPageItemResponse(
        Long userId,
        String username,
        String displayName,
        String email,
        String mobile,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
