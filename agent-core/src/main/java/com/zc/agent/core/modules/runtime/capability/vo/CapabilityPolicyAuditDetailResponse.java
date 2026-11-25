package com.zc.agent.core.modules.runtime.capability.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Capability Policy 决策详情及同一 Tool Call 的关联事件。
 *
 * @param decision 决策详情
 * @param correlatedEvents 同 Tool Call 关联事件
 */
public record CapabilityPolicyAuditDetailResponse(
        CapabilityPolicyAuditItemResponse decision,
        List<CorrelatedEvent> correlatedEvents
) {
    /**
     * 安全 Runtime Event 投影，不返回聊天正文、Tool 参数或审批意见。
     *
     * @param eventId 稳定事件 ID
     * @param sequenceNo Run 内序号
     * @param eventType 事件类型
     * @param payloadVersion Payload 版本
     * @param payload 安全业务载荷
     * @param metadata 安全扩展元数据
     * @param occurredAt 发生时间
     */
    public record CorrelatedEvent(
            String eventId,
            Long sequenceNo,
            String eventType,
            Integer payloadVersion,
            Map<String, Object> payload,
            Map<String, Object> metadata,
            LocalDateTime occurredAt
    ) {
    }
}
