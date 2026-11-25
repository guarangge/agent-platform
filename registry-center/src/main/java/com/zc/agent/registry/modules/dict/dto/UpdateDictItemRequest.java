package com.zc.agent.registry.modules.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编辑字典项请求。
 *
 * @param itemCode 字典项编码
 * @param itemName 字典项名称
 * @param sortOrder 排序值
 * @param status 状态
 * @param isDefault 是否默认推荐项
 * @param description 描述
 * @param remark 备注
 */
public record UpdateDictItemRequest(
        @NotBlank(message = "字典项编码不能为空")
        @Size(max = 64, message = "字典项编码长度不能超过64个字符")
        String itemCode,
        @NotBlank(message = "字典项名称不能为空")
        @Size(max = 128, message = "字典项名称长度不能超过128个字符")
        String itemName,
        Integer sortOrder,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @NotNull(message = "是否默认推荐项不能为空")
        Boolean isDefault,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
