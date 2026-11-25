package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/** Tool 版本快照请求。 */
public record ToolVersionRequest(
        @NotBlank String version,
        JsonNode inputSchema,
        JsonNode outputSchema,
        JsonNode invokeConfig,
        String releaseNotes,
        @NotBlank String compatibility,
        @NotBlank String status
) {
}
