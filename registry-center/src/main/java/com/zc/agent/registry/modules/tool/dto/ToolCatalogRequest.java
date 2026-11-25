package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tool 安装目录配置。 */
public record ToolCatalogRequest(
        @NotBlank @Size(max = 64) String catalogCode,
        @NotBlank @Size(max = 128) String catalogName,
        @NotBlank @Size(max = 32) String catalogType,
        @Size(max = 500) String sourceUrl,
        @Size(max = 64) String transportType,
        JsonNode config,
        @NotBlank @Size(max = 32) String status
) {
}
