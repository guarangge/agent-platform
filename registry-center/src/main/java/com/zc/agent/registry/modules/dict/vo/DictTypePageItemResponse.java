package com.zc.agent.registry.modules.dict.vo;

import java.time.LocalDateTime;

/**
 * 字典类型分页项响应。
 *
 * @param dictTypeId 字典类型 ID
 * @param typeCode 字典类型编码
 * @param typeName 字典类型名称
 * @param status 状态
 * @param builtIn 是否内置
 * @param description 描述
 * @param createdAt 创建时间
 */
public record DictTypePageItemResponse(
        Long dictTypeId,
        String typeCode,
        String typeName,
        String status,
        Boolean builtIn,
        String description,
        LocalDateTime createdAt
) {
}
