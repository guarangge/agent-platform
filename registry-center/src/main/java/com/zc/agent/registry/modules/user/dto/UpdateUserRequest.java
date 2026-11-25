package com.zc.agent.registry.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑用户请求。
 *
 * @param displayName 显示名称
 * @param email       邮箱
 * @param mobile      手机号
 * @param status      用户状态
 * @param remark      备注
 */
public record UpdateUserRequest(
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 128, message = "显示名称长度不能超过128个字符")
        String displayName,
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过128个字符")
        String email,
        @Pattern(regexp = "^$|^[0-9+\\-]{0,32}$", message = "手机号格式不正确")
        String mobile,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
