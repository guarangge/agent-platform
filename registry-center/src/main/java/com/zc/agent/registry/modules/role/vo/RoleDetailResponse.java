package com.zc.agent.registry.modules.role.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色详情响应。
 *
 * @param roleId      角色 ID
 * @param roleCode    角色编码
 * @param roleName    角色名称
 * @param status      角色状态
 * @param menuScope   菜单范围
 * @param description 角色描述
 * @param remark      备注
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param createdBy   创建人
 * @param updatedBy   更新人
 */
public record RoleDetailResponse(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        List<String> menuScope,
        String description,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
