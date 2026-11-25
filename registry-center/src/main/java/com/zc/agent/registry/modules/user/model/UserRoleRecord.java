package com.zc.agent.registry.modules.user.model;

import java.time.LocalDateTime;

/**
 * 用户角色记录。
 *
 * @param roleId      角色 ID
 * @param roleCode    角色编码
 * @param roleName    角色名称
 * @param status      角色状态
 * @param description 角色描述
 * @param boundAt     绑定时间
 */
public record UserRoleRecord(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        String description,
        LocalDateTime boundAt
) {
}
