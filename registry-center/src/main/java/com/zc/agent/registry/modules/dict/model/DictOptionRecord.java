package com.zc.agent.registry.modules.dict.model;

/**
 * 字典选项记录。
 *
 * @param typeCode 字典类型编码
 * @param itemCode 字典项编码
 * @param itemName 字典项名称
 * @param description 描述
 * @param sortOrder 排序值
 */
public record DictOptionRecord(
        String typeCode,
        String itemCode,
        String itemName,
        String description,
        Integer sortOrder
) {
}
