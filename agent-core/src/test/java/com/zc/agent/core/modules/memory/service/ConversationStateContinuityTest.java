package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationStateContinuityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuntimeLlmGatewayService llmGateway;
    private ConversationStateService stateService;

    @BeforeEach
    void setUp() {
        llmGateway = mock(RuntimeLlmGatewayService.class);
        when(llmGateway.sanitizeJsonText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        stateService = new ConversationStateService(
                objectMapper,
                new ConversationSlotExtractionService(llmGateway, objectMapper)
        );
    }

    @Test
    void shouldKeepFourTurnGenericStateWithPartialInformation() throws Exception {
        JsonNode metadata = metadata();
        ExecutionContext.ModelContext model = model();
        when(llmGateway.chatCompletion(anyString(), anyString(), eq(model)))
                .thenReturn("{\"slots\":{\"projectName\":\"Atlas\"}}")
                .thenReturn("{\"slots\":{\"environment\":\"production\"}}")
                .thenReturn("{\"slots\":{\"deadline\":\"2026-08-01\"}}")
                .thenReturn("{\"slots\":{\"approvalRequired\":\"true\"}}");

        String state = "{}";
        state = update(metadata, state, context(metadata, model, "项目名称是 Atlas"));
        state = update(metadata, state, context(metadata, model, "环境为 production"));
        state = update(metadata, state, context(metadata, model, "截止日期为 2026-08-01"));
        state = update(metadata, state, context(metadata, model, "需要审批是 true"));

        ExecutionContext.ConversationStateContext resolved = stateService.resolveConversationStateContext(
                metadata,
                objectMapper.readTree(state)
        );
        assertThat(resolved).isNotNull();
        assertThat(resolved.confirmedSlots())
                .extracting(ExecutionContext.StateSlotValue::key, ExecutionContext.StateSlotValue::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("projectName", "Atlas"),
                        org.assertj.core.groups.Tuple.tuple("environment", "production"),
                        org.assertj.core.groups.Tuple.tuple("deadline", "2026-08-01"),
                        org.assertj.core.groups.Tuple.tuple("approvalRequired", "true")
                );
        assertThat(resolved.missingSlots()).isEmpty();
    }

    @Test
    void shouldUseSchemaPatternWithoutModel() throws Exception {
        JsonNode metadata = objectMapper.readTree("""
                {"dialogState":{"enabled":true,"slots":[
                  {"key":"ticketId","label":"工单号","type":"string","required":true,
                   "aliases":["Ticket"],"extractionPattern":"(?:工单号|Ticket)[：:=\\\\s]+([A-Z]+-\\\\d+)","extractionGroup":1}
                ]}}
                """);
        ExecutionContext context = context(metadata, null, "请继续处理 Ticket OPS-2048");

        String state = update(metadata, "{}", context);
        ExecutionContext.ConversationStateContext resolved = stateService.resolveConversationStateContext(metadata, objectMapper.readTree(state));

        assertThat(resolved.confirmedSlots()).singleElement()
                .satisfies(slot -> assertThat(slot.value()).isEqualTo("OPS-2048"));
    }

    private String update(JsonNode metadata, String state, ExecutionContext context) {
        return stateService.updateConversationMetadata(metadata, state, context, "已记录并继续处理");
    }

    private JsonNode metadata() throws Exception {
        return objectMapper.readTree("""
                {"dialogState":{"enabled":true,"slots":[
                  {"key":"projectName","label":"项目名称","type":"string","required":true},
                  {"key":"environment","label":"环境","type":"string","required":true,"enumValues":["test","staging","production"]},
                  {"key":"deadline","label":"截止日期","type":"date","required":true},
                  {"key":"approvalRequired","label":"需要审批","type":"boolean","required":true}
                ]}}
                """);
    }

    private ExecutionContext context(JsonNode metadata, ExecutionContext.ModelContext model, String userMessage) {
        return new ExecutionContext(
                new ExecutionContext.CurrentUserContext(1L, "tester", "Tester", List.of(), List.of()),
                null, null, null, null, null, List.of(),
                new ExecutionContext.ConversationContext(1L, "CONV_TEST", "连续性测试", "processing", null),
                new ExecutionContext.AgentContext(1L, "GENERAL_AGENT", "通用 Agent", null, "enabled", metadata),
                null, null, null,
                new ExecutionContext.TimeContext("2026-07-15", "2026-07-15T15:00:00", "Asia/Shanghai"),
                model,
                Map.of(),
                new ExecutionContext.MessageContext(1L, userMessage),
                new ExecutionContext.TaskContext(1L, "TASK_TEST", "TRACE_TEST"),
                List.of(), List.of(), List.of()
        );
    }

    private ExecutionContext.ModelContext model() {
        return new ExecutionContext.ModelContext(
                "TEST_MODEL", "Test", "openai_compatible", "chat", "http://localhost", "test-key", "/v1/chat/completions",
                "test-model", 0.1d, 1024, 8192, 1024, 256, 0.8d, "cl100k_base", 5, "enabled", null
        );
    }
}
