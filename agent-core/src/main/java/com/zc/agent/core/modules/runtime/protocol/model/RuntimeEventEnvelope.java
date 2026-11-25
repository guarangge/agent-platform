package com.zc.agent.core.modules.runtime.protocol.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对外稳定的 Runtime Event Envelope。
 *
 * @param eventId       稳定事件 ID
 * @param sequenceNo    Run 内单调递增序号
 * @param threadId      当前映射的 Conversation ID
 * @param turnId        Turn ID，首批协议暂未单独持久化时为空
 * @param runId         Agent Run 编码
 * @param taskId        关联 Task ID
 * @param itemId        关联 Item ID，首批协议为空
 * @param eventType     统一事件类型
 * @param occurredAt    事件发生时间
 * @param payloadVersion Payload Schema 版本
 * @param payload       类型相关业务载荷
 * @param metadata      安全扩展元数据
 */
public record RuntimeEventEnvelope(
        String eventId,
        Long sequenceNo,
        Long threadId,
        String turnId,
        String runId,
        Long taskId,
        String itemId,
        String eventType,
        LocalDateTime occurredAt,
        Integer payloadVersion,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {
}
