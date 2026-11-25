package com.zc.agent.registry.modules.user.model;

import java.time.LocalDateTime;

/**
 * 用户详情数据。
 *
 * @param userId       用户 ID
 * @param username     登录账号
 * @param displayName  显示名称
 * @param email        邮箱
 * @param mobile       手机号
 * @param status       用户状态
 * @param lastLoginAt  最近登录时间
 * @param remark       备注
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param createdBy    创建人
 * @param updatedBy    更新人
 */
public record UserDetailRecord(
        Long userId,
        String username,
        String displayName,
        String email,
        String mobile,
        String status,
        LocalDateTime lastLoginAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
