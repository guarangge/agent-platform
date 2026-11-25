package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Tool 部署实例配置。 */
public record ToolDeploymentRequest(
        @NotNull Long versionId,
        @NotBlank String deploymentCode,
        @NotBlank String deploymentName,
        @NotBlank String environmentCode,
        @NotBlank String providerType,
        @NotBlank String transportType,
        String endpoint,
        @NotBlank String authType,
        JsonNode authConfig,
        JsonNode headers,
        JsonNode invokeConfig,
        @Min(0) Integer trafficWeight,
        @Min(0) @Max(100) Integer rolloutPercent,
        Integer priority,
        @Min(1) Integer timeoutSeconds,
        @Min(0) Integer retryTimes,
        @Min(1) Integer failureThreshold,
        @Min(1) Integer recoverySeconds,
        @NotBlank String status,
        JsonNode metadata
) {
}
