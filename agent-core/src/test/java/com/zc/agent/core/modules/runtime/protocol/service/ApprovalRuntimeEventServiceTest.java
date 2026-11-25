package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRuntimeEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldCreateApprovalAndPublishRequestedWithoutPrivateMetadata() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(conversationRepository.insertApproval(
                8L, 12L, 31L, "approval-1", "高风险Tool人工审批", "{\"secret\":\"value\"}", 1L))
                .thenReturn(55L);
        ApprovalRuntimeEventService service = new ApprovalRuntimeEventService(
                conversationRepository, runRepository, publisher, new ObjectMapper());
        AgentRunStreamingContext.bind("RUN_1");

        Long approvalId = service.request(new ApprovalRuntimeEventService.RequestApprovalCommand(
                8L, 12L, 31L, "approval-1", "高风险Tool人工审批", "step_guardrail",
                "{\"secret\":\"value\"}", "TRACE_1", 1L));

        assertThat(approvalId).isEqualTo(55L);
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.APPROVAL_REQUESTED),
                argThat(payload -> Long.valueOf(55L).equals(payload.get("approvalId"))
                        && "pending".equals(payload.get("status"))
                        && !payload.toString().contains("secret")),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldPublishSafeCapabilityIdentityForApprovalCorrelation() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        String parameterHash = "a".repeat(64);
        String metadata = "{\"policyType\":\"capability\",\"toolCallId\":\"TOOL_CALL_1234567890ABCDEF1234\","
                + "\"parameterHash\":\"" + parameterHash + "\",\"target\":\"/private/file\"}";
        when(conversationRepository.insertApproval(
                8L, 12L, 31L, "capability:FILE_WRITE:1", "能力审批", metadata, 1L))
                .thenReturn(56L);
        ApprovalRuntimeEventService service = new ApprovalRuntimeEventService(
                conversationRepository, runRepository, publisher, new ObjectMapper());
        AgentRunStreamingContext.bind("RUN_1");

        service.request(new ApprovalRuntimeEventService.RequestApprovalCommand(
                8L, 12L, 31L, "capability:FILE_WRITE:1", "能力审批", "capability_policy",
                metadata, "TRACE_1", 1L));

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.APPROVAL_REQUESTED),
                argThat(payload -> "TOOL_CALL_1234567890ABCDEF1234".equals(payload.get("toolCallId"))
                        && parameterHash.equals(payload.get("parameterHash"))
                        && !payload.toString().contains("/private/file")),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldResolveOriginalRunByTaskWhenApprovalIsApprovedLater() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(runRepository.findRunCodeByTaskId(12L)).thenReturn(Optional.of("RUN_1"));
        ApprovalRuntimeEventService service = new ApprovalRuntimeEventService(
                conversationRepository, runRepository, publisher, new ObjectMapper());
        ApprovalRecord approval = approval("pending");

        service.transition(new ApprovalRuntimeEventService.TransitionApprovalCommand(
                approval, "approved", "包含用户私密审批意见", "step_guardrail", "TRACE_1", 2L));

        verify(conversationRepository).updateApprovalStatus(
                55L, "approved", "包含用户私密审批意见", 2L);
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.APPROVAL_RESOLVED),
                argThat(payload -> "approved".equals(payload.get("status"))
                        && Long.valueOf(2L).equals(payload.get("resolvedBy"))
                        && !payload.toString().contains("私密审批意见")),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldPublishDedicatedRevokedEvent() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        when(runRepository.findRunCodeByTaskId(12L)).thenReturn(Optional.of("RUN_1"));
        ApprovalRuntimeEventService service = new ApprovalRuntimeEventService(
                conversationRepository, runRepository, publisher, new ObjectMapper());

        service.transition(new ApprovalRuntimeEventService.TransitionApprovalCommand(
                approval("pending"), "revoked", "撤回", "workflow", "TRACE_1", 1L));

        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.APPROVAL_REVOKED),
                argThat(payload -> "revoked".equals(payload.get("status"))),
                eq(Map.of("traceId", "TRACE_1")));
    }

    @Test
    void shouldRestoreOriginalRunScopeDuringApprovalResume() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        AgentRunQueueRepository runRepository = mock(AgentRunQueueRepository.class);
        when(runRepository.findRunCodeByTaskId(12L)).thenReturn(Optional.of("RUN_1"));
        ApprovalRuntimeEventService service = new ApprovalRuntimeEventService(
                conversationRepository, runRepository, mock(RuntimeEventPublisher.class), new ObjectMapper());

        try (var ignored = service.openRunScope(12L)) {
            assertThat(AgentRunStreamingContext.currentRunCode()).isEqualTo("RUN_1");
        }

        assertThat(AgentRunStreamingContext.currentRunCode()).isNull();
    }

    private ApprovalRecord approval(String status) {
        return new ApprovalRecord(
                55L, 8L, 12L, 31L, "approval-1", "高风险Tool人工审批",
                status, null, null, null, "{}", LocalDateTime.now());
    }
}
