package com.zc.agent.registry.modules.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 角色分页查询请求。
 *
 * @param pageNum  页码
 * @param pageSize 每页条数
 * @param roleCode 角色编码
 * @param roleName 角色名称
 * @param status   角色状态
 */
public record RolePageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "角色编码长度不能超过64个字符")
        String roleCode,
        @Size(max = 128, message = "角色名称长度不能超过128个字符")
        String roleName,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
