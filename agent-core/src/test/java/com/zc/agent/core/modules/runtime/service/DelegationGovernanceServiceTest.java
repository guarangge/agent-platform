package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelegationGovernanceServiceTest {
    @Test
    void shouldSelectDelegateByAllRequiredCapabilities() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 0, 0);
        DelegationGovernanceService service = new DelegationGovernanceService(jdbc);
        ExecutionContext context = mock(ExecutionContext.class);
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.putObject("delegationPolicy").put("maxDepth", 3).put("maxChildrenPerTask", 5);
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                1L, "ROOT", "主 Agent", "MODEL", "enabled", metadata));
        when(context.availableDelegates()).thenReturn(List.of(
                new ExecutionContext.DelegateAgent(2L, "JAVA_REVIEW", "Java 评审", "Java Security code review"),
                new ExecutionContext.DelegateAgent(3L, "WRITER", "文案", "copy writing")
        ));

        var selection = service.selectAndValidate(context,
                Map.of("requiredCapabilities", List.of("Java", "Security")), 8L);

        assertThat(selection.delegate().agentId()).isEqualTo(2L);
        assertThat(selection.depth()).isEqualTo(1);
        assertThat(selection.selectionReason()).contains("requiredCapabilities");
    }
}
