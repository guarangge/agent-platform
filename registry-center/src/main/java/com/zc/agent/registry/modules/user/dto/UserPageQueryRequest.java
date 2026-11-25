package com.zc.agent.registry.modules.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 用户分页查询请求。
 *
 * @param pageNum     页码
 * @param pageSize    每页条数
 * @param username    用户名
 * @param displayName 显示名称
 * @param status      用户状态
 */
public record UserPageQueryRequest(
        @Min(value = 1, message = "页码最小为1")
        Integer pageNum,
        @Min(value = 1, message = "每页条数最小为1")
        @Max(value = 100, message = "每页条数不能超过100")
        Integer pageSize,
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,
        @Size(max = 128, message = "显示名称长度不能超过128个字符")
        String displayName,
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
