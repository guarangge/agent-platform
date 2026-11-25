package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Runtime Protocol 稳定事件信封。
 *
 * @param eventId 事件 ID
 * @param sequenceNo Run 内单调序号
 * @param threadId Thread ID
 * @param turnId Turn ID
 * @param runId Run 编码
 * @param taskId Task ID
 * @param itemId 关联 Item ID
 * @param eventType 事件类型
 * @param occurredAt 发生时间
 * @param payloadVersion Payload 版本
 * @param payload 事件安全 Payload
 * @param metadata 事件安全元数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeEventEnvelope(
        String eventId,
        Long sequenceNo,
        Long threadId,
        String turnId,
        String runId,
        Long taskId,
        String itemId,
        String eventType,
        String occurredAt,
        Integer payloadVersion,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {
}
