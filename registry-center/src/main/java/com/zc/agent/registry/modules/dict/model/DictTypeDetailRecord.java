package com.zc.agent.registry.modules.dict.model;

import java.time.LocalDateTime;

/**
 * 字典类型详情记录。
 *
 * @param dictTypeId 字典类型 ID
 * @param typeCode 字典类型编码
 * @param typeName 字典类型名称
 * @param status 状态
 * @param builtIn 是否内置
 * @param description 描述
 * @param remark 备注
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param createdBy 创建人
 * @param updatedBy 更新人
 */
public record DictTypeDetailRecord(
        Long dictTypeId,
        String typeCode,
        String typeName,
        String status,
        Boolean builtIn,
        String description,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long createdBy,
        Long updatedBy
) {
}
