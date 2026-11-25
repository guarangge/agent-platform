package com.zc.agent.registry.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 切换当前租户作用域请求。 */
public record SwitchScopeRequest(
        @NotNull(message = "成员关系ID不能为空")
        @Positive(message = "成员关系ID必须为正整数")
        Long membershipId
) {
}
