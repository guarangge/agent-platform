package com.zc.agent.registry.modules.tool.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Agent Tool 能力包覆盖更新请求。
 *
 * @param packIds 能力包 ID 列表
 */
public record AgentToolCapabilityPackRequest(List<@NotNull Long> packIds) {
}
