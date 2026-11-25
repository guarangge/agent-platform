package com.zc.agent.registry.modules.prompt.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增 Prompt 请求。
 *
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
 */
public record CreatePromptRequest(
        @NotBlank(message = "Prompt 编码不能为空")
        @Size(max = 64, message = "Prompt 编码长度不能超过64个字符")
        String promptCode,
        @NotBlank(message = "Prompt 名称不能为空")
        @Size(max = 128, message = "Prompt 名称长度不能超过128个字符")
        String promptName,
        @NotBlank(message = "Prompt 类型不能为空")
        @Size(max = 64, message = "Prompt 类型长度不能超过64个字符")
        String promptType,
        @NotBlank(message = "Prompt 分类不能为空")
        @Size(max = 64, message = "Prompt 分类长度不能超过64个字符")
        String promptCategory,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @NotBlank(message = "Prompt 正文不能为空")
        String content,
        @NotBlank(message = "版本不能为空")
        @Size(max = 32, message = "版本长度不能超过32个字符")
        String version,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @NotNull(message = "扩展元数据不能为空")
        JsonNode metadata,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
