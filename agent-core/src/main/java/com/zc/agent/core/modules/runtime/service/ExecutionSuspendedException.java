package com.zc.agent.core.modules.runtime.service;

/** 长任务已持久化等待并释放当前 Worker。 */
public class ExecutionSuspendedException extends RuntimeException {
    private final String eventKey;

    public ExecutionSuspendedException(String message, String eventKey) {
        super(message);
        this.eventKey = eventKey;
    }

    public String eventKey() {
        return eventKey;
    }
}
