package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/** 远程 Tool 发现请求。 */
public record ToolDiscoveryRequest(
        Long catalogId,
        @NotBlank String providerType,
        @NotBlank String transportType,
        String endpoint,
        JsonNode authConfig,
        JsonNode headers,
        JsonNode invokeConfig
) {
}
