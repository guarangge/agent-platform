package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;

import java.util.Map;

/**
 * Runtime Event 发布扩展点，执行内核不依赖具体事件存储实现。
 */
public interface RuntimeEventPublisher {

    /**
     * 发布一条版本化 Runtime Event。
     *
     * @param runCode  Agent Run 编码
     * @param eventType 标准事件类型
     * @param content  事件正文
     * @param metadata 安全扩展元数据
     * @return Run 内事件序号
     */
    long publish(String runCode, RuntimeEventType eventType, String content, Map<String, Object> metadata);

    /** 发布结构化 Runtime Event Payload。 */
    long publishPayload(String runCode,
                        RuntimeEventType eventType,
                        Map<String, Object> payload,
                        Map<String, Object> metadata);
}
