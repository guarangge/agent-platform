package com.zc.agent.registry.modules.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Tool 实例灰度路由更新请求。 */
public record ToolRouteUpdateRequest(
        @Min(0) Integer trafficWeight,
        @Min(0) @Max(100) Integer rolloutPercent,
        Integer priority,
        @Min(1) Integer failureThreshold,
        @Min(1) Integer recoverySeconds,
        @NotBlank String status
) {
}
