package com.zc.agent.registry.modules.tool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Tool 能力包创建与更新请求。
 *
 * @param packCode 能力包编码
 * @param packName 能力包名称
 * @param description 能力包说明
 * @param version 能力包版本
 * @param status 能力包状态
 * @param toolIds Tool ID 列表
 * @param metadata 扩展配置
 */
public record ToolCapabilityPackRequest(
        @NotBlank @Size(max = 64) String packCode,
        @NotBlank @Size(max = 128) String packName,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 64) String version,
        @NotBlank @Size(max = 32) String status,
        @NotEmpty List<@NotNull Long> toolIds,
        JsonNode metadata
) {
}
