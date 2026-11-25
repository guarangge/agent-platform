package com.zc.agent.registry.modules.prompt.model;

import java.time.LocalDateTime;

/**
 * Prompt 分页记录。
 *
 * @param promptId        Prompt ID
 * @param promptCode      Prompt 编码
 * @param promptName      Prompt 名称
 * @param promptType      Prompt 类型
 * @param promptCategory  Prompt 分类
 * @param version         版本
 * @param status          状态
 * @param description     描述
 * @param createdAt       创建时间
 */
public record PromptPageRecord(
        Long promptId,
        String promptCode,
        String promptName,
        String promptType,
        String promptCategory,
        String version,
        String status,
        String description,
        LocalDateTime createdAt
) {
}
