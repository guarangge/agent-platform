package com.zc.agent.registry.modules.prompt.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Prompt 详情响应。
 *
 * @param promptId        Prompt ID
 * @param promptCode      Prompt 编码
 * @param promptName      Prompt 名称
 * @param promptType      Prompt 类型
 * @param promptCategory  Prompt 分类
 * @param description     描述
 * @param content         Prompt 正文
 * @param version         版本
 * @param status          状态
 * @param metadata        扩展元数据
 * @param remark          备注
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @param createdBy       创建人
 * @param updatedBy       更新人
 */
public record PromptDetailResponse(
        Long promptId,
        String promptCode,
        String promptName,
        String promptType,
        String promptCategory,
        String description,
        String content,
        String version,
        String status,
        JsonNode metadata,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
