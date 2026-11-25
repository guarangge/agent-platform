package com.zc.agent.registry.modules.prompt.vo;

import java.time.LocalDateTime;

/**
 * Prompt 分页项响应。
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
public record PromptPageItemResponse(
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
