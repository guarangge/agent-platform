package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopBudgetState;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopMetrics;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 发布 Interactive Loop 的确定性停止事实。 */
@Service
public class InteractiveLoopRuntimeEventService {

    private final RuntimeEventPublisher eventPublisher;
    private final InteractiveLoopMetrics metrics;

    public InteractiveLoopRuntimeEventService(RuntimeEventPublisher eventPublisher,
                                              InteractiveLoopMetrics metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /** 当前线程绑定 Run 时发布 run.loop.stopped，快照不包含 Tool 指纹或参数。 */
    public void stopped(Long taskId,
                        String traceId,
                        InteractiveLoopBudgetState.Snapshot snapshot) {
        if (snapshot == null || snapshot.stopReason() == null) {
            throw new IllegalArgumentException("Interactive Loop 停止事件必须包含停止原因");
        }
        metrics.recordStopped(snapshot);
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) {
            return;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("stopReason", snapshot.stopReason().code());
        payload.put("iterations", snapshot.iterations());
        payload.put("llmCalls", snapshot.llmCalls());
        payload.put("toolCalls", snapshot.toolCalls());
        payload.put("tokens", snapshot.tokens());
        payload.put("costAmount", snapshot.costAmount());
        payload.put("uniqueToolFingerprints", snapshot.uniqueToolFingerprints());
        payload.put("maxIterations", snapshot.maxIterations());
        payload.put("maxLlmCalls", snapshot.maxLlmCalls());
        payload.put("maxToolCalls", snapshot.maxToolCalls());
        payload.put("maxTokens", snapshot.maxTokens());
        payload.put("maxCostAmount", snapshot.maxCostAmount());
        payload.put("duplicateToolCallLimit", snapshot.duplicateToolCallLimit());
        Map<String, Object> metadata = traceId == null || traceId.isBlank()
                ? Map.of() : Map.of("traceId", traceId);
        eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_LOOP_STOPPED, payload, metadata);
    }
}
