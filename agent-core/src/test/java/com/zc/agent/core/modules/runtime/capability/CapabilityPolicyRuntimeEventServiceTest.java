package com.zc.agent.core.modules.runtime.capability;

import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventPublisher;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityPolicyRuntimeEventServiceTest {

    @Test
    void shouldPublishOnlySafeDecisionFactsBeforeInvocation() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        CapabilityPolicyRuntimeEventService service = new CapabilityPolicyRuntimeEventService(publisher);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.taskContext()).thenReturn(new ExecutionContext.TaskContext(9L, "TASK_9", "TRACE_9"));
        RegistryAgentTool tool = mock(RegistryAgentTool.class);
        when(tool.toolCode()).thenReturn("FILE_DELETE");
        when(tool.toolName()).thenReturn("删除文件");
        CapabilityPolicyDecision decision = new CapabilityPolicyDecision(
                "approval", "protected-file", "保护目录策略", 2, "需要审批",
                List.of("filesystem.write"), "irreversible", "parameter-hash", "target-hash",
                "capability:FILE_DELETE:1234567890abcdef", false,
                Map.of("requestedAction", "approval"));

        try (AgentRunStreamingContext.Scope ignored = AgentRunStreamingContext.openRunScope("RUN_9")) {
            service.decided(context, tool, decision, "TOOL_CALL_9");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publishPayload(
                org.mockito.ArgumentMatchers.eq("RUN_9"),
                org.mockito.ArgumentMatchers.eq(RuntimeEventType.POLICY_DECIDED),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Map.of("traceId", "TRACE_9")));
        assertThat(payloadCaptor.getValue())
                .containsEntry("toolCallId", "TOOL_CALL_9")
                .containsEntry("parameterHash", "parameter-hash")
                .containsEntry("targetHash", "target-hash")
                .doesNotContainKeys("arguments", "target", "inputText");
    }
}
