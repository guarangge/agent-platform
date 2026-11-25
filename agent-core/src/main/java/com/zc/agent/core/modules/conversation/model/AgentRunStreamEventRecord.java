package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;

/**
 * Agent Run 原生流事件。
 *
 * @param eventId    事件主键
 * @param runCode    Run 编码
 * @param sequenceNo Run 内单调递增序号
 * @param eventType  事件类型
 * @param content    正文增量
 * @param metadata   扩展元数据 JSON
 * @param payloadVersion Payload Schema 版本
 * @param createdAt  创建时间
 */
public record AgentRunStreamEventRecord(
        Long eventId,
        String runCode,
        Long sequenceNo,
        String eventType,
        String content,
        String metadata,
        Integer payloadVersion,
        LocalDateTime createdAt
) {
    /** 保留历史测试和调用方构造契约，默认使用 Payload V1。 */
    public AgentRunStreamEventRecord(Long eventId,
                                     String runCode,
                                     Long sequenceNo,
                                     String eventType,
                                     String content,
                                     String metadata,
                                     LocalDateTime createdAt) {
        this(eventId, runCode, sequenceNo, eventType, content, metadata, 1, createdAt);
    }
}
