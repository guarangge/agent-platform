package com.zc.agent.core.modules.sandbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Sandbox 配置分页查询。
 */
public record SandboxProfileQueryRequest(
        @Min(1) Integer pageNum,
        @Min(1) @Max(200) Integer pageSize,
        String keyword,
        String status
) {
}
