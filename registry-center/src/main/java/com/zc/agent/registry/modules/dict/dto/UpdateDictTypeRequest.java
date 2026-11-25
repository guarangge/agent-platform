package com.zc.agent.registry.modules.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 编辑字典类型请求。
 *
 * @param typeCode 字典类型编码
 * @param typeName 字典类型名称
 * @param status 状态
 * @param builtIn 是否内置
 * @param description 描述
 * @param remark 备注
 */
public record UpdateDictTypeRequest(
        @NotBlank(message = "字典类型编码不能为空")
        @Size(max = 64, message = "字典类型编码长度不能超过64个字符")
        String typeCode,
        @NotBlank(message = "字典类型名称不能为空")
        @Size(max = 128, message = "字典类型名称长度不能超过128个字符")
        String typeName,
        @NotBlank(message = "状态不能为空")
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status,
        @NotNull(message = "是否内置不能为空")
        Boolean builtIn,
        @Size(max = 500, message = "描述长度不能超过500个字符")
        String description,
        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
