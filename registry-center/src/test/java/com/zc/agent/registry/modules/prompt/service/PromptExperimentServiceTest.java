package com.zc.agent.registry.modules.prompt.service;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.prompt.dto.PromptExperimentRequests.ExperimentRequest;
import com.zc.agent.registry.modules.prompt.dto.PromptExperimentRequests.ResolveRequest;
import com.zc.agent.registry.modules.prompt.dto.PromptExperimentRequests.VariantRequest;
import com.zc.agent.registry.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptExperimentServiceTest {

    @Test
    void shouldKeepConversationAllocationStable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                row(9L, "control", 11L, 50),
                row(9L, "candidate", 12L, 50)
        ));
        PromptExperimentService service = new PromptExperimentService(jdbc);
        ResolveRequest request = new ResolveRequest(11L, 7L, 101L, 3L, "system");

        Map<String, Object> first = service.resolve(request);
        Map<String, Object> second = service.resolve(request);

        assertThat(first.get("matched")).isEqualTo(true);
        assertThat(second.get("variant_code")).isEqualTo(first.get("variant_code"));
        assertThat(second.get("prompt_id")).isEqualTo(first.get("prompt_id"));
    }

    @Test
    void shouldRejectExperimentWithoutSingleControlVariantBeforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PromptExperimentService service = new PromptExperimentService(jdbc);
        ExperimentRequest request = new ExperimentRequest("EXP", "实验", 1L, null,
                "system", "conversation", 100, "running", List.of(
                new VariantRequest("a", "A", 1L, 50, false, "enabled"),
                new VariantRequest("b", "B", 2L, 50, false, "enabled")
        ));

        assertThatThrownBy(() -> service.create(request, mock(SecurityUser.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("control Variant");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private Map<String, Object> row(long experimentId, String variantCode, long promptId, int weight) {
        return Map.of(
                "experiment_id", experimentId,
                "experiment_code", "EXP",
                "base_prompt_id", 11L,
                "allocation_key", "conversation",
                "rollout_percent", 100,
                "variant_id", promptId,
                "variant_code", variantCode,
                "prompt_id", promptId,
                "weight", weight
        );
    }
}
