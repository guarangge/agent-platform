package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.kernel.RuntimeExecutionModePolicy;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 发布本次 Run 的双执行模式确定性路由结果。 */
@Service
public class RuntimeExecutionModeEventService {

    private final RuntimeEventPublisher eventPublisher;

    public RuntimeExecutionModeEventService(RuntimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 当前线程绑定持久化 Run 时发布 run.mode.selected。 */
    public void selected(Long taskId,
                         String traceId,
                         RuntimeExecutionModePolicy.Selection selection) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) {
            return;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("requestedMode", selection.requestedMode().code());
        payload.put("effectiveMode", selection.effectiveMode().code());
        payload.put("allowedModes", selection.allowedModes().stream().map(item -> item.code()).toList());
        payload.put("reason", selection.reason());
        payload.put("fallback", selection.fallback());
        if (selection.rolloutBucket() != null) {
            payload.put("rolloutBucket", selection.rolloutBucket());
        }
        Map<String, Object> metadata = traceId == null || traceId.isBlank()
                ? Map.of() : Map.of("traceId", traceId);
        eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_MODE_SELECTED, payload, metadata);
    }
}
