package com.zc.agent.registry.modules.dict.vo;

import java.time.LocalDateTime;

/**
 * 字典项分页项响应。
 *
 * @param dictItemId 字典项 ID
 * @param dictTypeId 字典类型 ID
 * @param typeCode 字典类型编码
 * @param itemCode 字典项编码
 * @param itemName 字典项名称
 * @param sortOrder 排序值
 * @param status 状态
 * @param isDefault 是否默认推荐项
 * @param description 描述
 * @param createdAt 创建时间
 */
public record DictItemPageItemResponse(
        Long dictItemId,
        Long dictTypeId,
        String typeCode,
        String itemCode,
        String itemName,
        Integer sortOrder,
        String status,
        Boolean isDefault,
        String description,
        LocalDateTime createdAt
) {
}
