package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 将 Catalog 条目安装为平台 Tool 的请求。 */
public record ToolInstallRequest(
        @NotNull Long catalogItemId,
        @NotBlank String toolCode,
        @NotBlank String toolName,
        @NotBlank String toolType,
        @NotBlank String toolCategory,
        @NotBlank String permissionLevel,
        @NotBlank String version,
        @NotBlank String providerType,
        @NotBlank String transportType,
        String endpoint,
        @NotBlank String authType,
        @NotBlank String environmentCode,
        JsonNode authConfig,
        JsonNode headers,
        JsonNode invokeConfig
) {
}
