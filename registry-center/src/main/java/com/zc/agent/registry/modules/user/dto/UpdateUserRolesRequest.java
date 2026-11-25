package com.zc.agent.registry.modules.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 覆盖更新用户角色请求。
 *
 * @param roleIds 角色 ID 列表，传空数组表示清空用户角色
 */
public record UpdateUserRolesRequest(
        @NotNull(message = "角色 ID 列表不能为空")
        @Size(max = 200, message = "角色数量不能超过200个")
        List<Long> roleIds
) {
}
