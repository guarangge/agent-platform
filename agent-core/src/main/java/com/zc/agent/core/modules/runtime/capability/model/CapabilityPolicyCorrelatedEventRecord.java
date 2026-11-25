package com.zc.agent.core.modules.runtime.capability.model;

import java.time.LocalDateTime;

/**
 * 同一 Tool Call ID 关联的 Runtime Event 数据库投影。
 *
 * @param eventId 事件主键
 * @param sequenceNo Run 内序号
 * @param eventType 事件类型
 * @param content 安全 Payload JSON
 * @param metadata 安全 Metadata JSON
 * @param payloadVersion Payload 版本
 * @param createdAt 发生时间
 */
public record CapabilityPolicyCorrelatedEventRecord(
        Long eventId,
        Long sequenceNo,
        String eventType,
        String content,
        String metadata,
        Integer payloadVersion,
        LocalDateTime createdAt
) {
}
