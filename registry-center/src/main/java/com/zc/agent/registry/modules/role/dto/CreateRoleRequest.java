package com.zc.agent.registry.modules.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增角色请求。
 *
 * @param roleCode    角色编码
 * @param roleName    角色名称
 * @param status      角色状态
 * @param menuScope   菜单范围
 * @param description 角色描述
 * @param remark      备注
 */
public record CreateRoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码长度不能超过64个字符")
        String roleCode,
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 128, message = "角色名称长度不能超过128个字符")
        String roleName,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @NotEmpty(message = "菜单范围不能为空")
        @Size(max = 100, message = "菜单范围数量不能超过100个")
        List<@NotBlank(message = "菜单范围项不能为空")
        @Size(max = 64, message = "菜单范围项长度不能超过64个字符") String> menuScope,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
