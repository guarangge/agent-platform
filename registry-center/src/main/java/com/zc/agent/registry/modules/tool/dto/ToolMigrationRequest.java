package com.zc.agent.registry.modules.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Tool 版本迁移请求。 */
public record ToolMigrationRequest(
        Long sourceVersionId,
        @NotNull Long targetVersionId,
        @NotBlank String strategy,
        @Min(0) @Max(100) Integer rolloutPercent
) {
}
