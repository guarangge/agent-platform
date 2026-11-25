package com.zc.agent.registry.modules.dict.vo;

/**
 * 字典下拉项响应。
 *
 * @param itemCode 字典项编码
 * @param itemName 字典项名称
 * @param description 描述
 */
public record DictOptionItemResponse(
        String itemCode,
        String itemName,
        String description
) {
}
