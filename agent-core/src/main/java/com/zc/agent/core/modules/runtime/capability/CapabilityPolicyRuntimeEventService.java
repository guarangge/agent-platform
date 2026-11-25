package com.zc.agent.core.modules.runtime.capability;

import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventPublisher;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 发布不包含 Tool 参数原文和目标资源原文的 Capability Policy 决策事件。 */
@Service
public class CapabilityPolicyRuntimeEventService {

    private final RuntimeEventPublisher eventPublisher;

    public CapabilityPolicyRuntimeEventService(RuntimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 发布调用前策略决策，事件写入失败时阻止尚未发生的 Tool 调用。 */
    public void decided(ExecutionContext context,
                        RegistryAgentTool tool,
                        CapabilityPolicyDecision decision,
                        String toolCallId) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode == null || runCode.isBlank()) {
            return;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCode", tool.toolCode());
        payload.put("toolName", tool.toolName());
        payload.put("toolCallId", toolCallId);
        payload.put("decision", decision.action());
        payload.put("requestedAction", decision.metadata().get("requestedAction"));
        payload.put("policyCode", decision.policyCode());
        payload.put("policyVersion", decision.policyVersion());
        payload.put("runtimeCapabilities", decision.matchedCapabilities());
        payload.put("sideEffectLevel", decision.sideEffectLevel());
        payload.put("parameterHash", decision.parameterHash());
        payload.put("targetHash", decision.targetHash() == null ? "" : decision.targetHash());
        payload.put("approvalNodeId", decision.approvalNodeId() == null ? "" : decision.approvalNodeId());
        payload.put("approvalGranted", decision.approvalGranted());
        if (context.taskContext() != null && context.taskContext().taskId() != null) {
            payload.put("taskId", context.taskContext().taskId());
        }
        Map<String, Object> metadata = context.taskContext() == null
                || context.taskContext().traceId() == null
                ? Map.of()
                : Map.of("traceId", context.taskContext().traceId());
        eventPublisher.publishPayload(runCode, RuntimeEventType.POLICY_DECIDED, payload, metadata);
    }
}
