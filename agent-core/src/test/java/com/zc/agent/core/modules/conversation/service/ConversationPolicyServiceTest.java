package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.dto.CreateConversationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationPolicyServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationPolicyService service = new ConversationPolicyService(objectMapper);

    @Test
    void shouldBuildConversationOverridesAndKeepAgentDefaults() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest(1L, "会话", true,
                "summary_and_facts", true, true, 10, 5, true, 8, 0.5D, 80);

        JsonNode metadata = objectMapper.readTree(service.buildMetadata(null, request));

        assertThat(metadata.path("compactionPolicy").path("maxRecentMessages").asInt()).isEqualTo(10);
        assertThat(metadata.path("agentDefaultCompactionPolicy").path("maxRecentMessages").asInt()).isEqualTo(6);
        assertThat(service.resolveEffective(metadata).maxRecentMessages()).isEqualTo(10);
    }

    @Test
    void shouldRejectUnsupportedMemoryMode() {
        CreateConversationRequest request = new CreateConversationRequest(1L, null, true,
                "unknown", true, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.buildMetadata(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户历史记忆模式不合法");
    }

    @Test
    void shouldPersistStreamingPreferenceWithoutOverwritingOtherMetadata() throws Exception {
        JsonNode current = objectMapper.readTree("""
                {"compactionPolicy":{"maxRecentMessages":8},"custom":"kept"}
                """);

        JsonNode enabled = objectMapper.readTree(service.mergeInteractionPreference(current, true));
        JsonNode disabled = objectMapper.readTree(service.mergeInteractionPreference(enabled, false));

        assertThat(enabled.path("interactionPolicy").path("responseStreamingEnabled").asBoolean()).isTrue();
        assertThat(enabled.path("compactionPolicy").path("maxRecentMessages").asInt()).isEqualTo(8);
        assertThat(enabled.path("custom").asText()).isEqualTo("kept");
        assertThat(service.resolveResponseStreamingEnabled(enabled)).isTrue();
        assertThat(service.resolveResponseStreamingEnabled(disabled)).isFalse();
        assertThat(service.resolveResponseStreamingEnabled(null)).isFalse();
    }
}
