package com.zc.agent.core.modules.runtime.capability.repository;

import com.zc.agent.core.modules.runtime.capability.dto.CapabilityPolicyAuditQueryRequest;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyAuditScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityPolicyAuditRepositoryTest {

    @Test
    void shouldAlwaysScopeAuditQueryToRunOwnerAndPolicyEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        CapabilityPolicyAuditRepository repository = new CapabilityPolicyAuditRepository(jdbcTemplate);

        repository.count(new CapabilityPolicyAuditScope(7L, 2L, 3L, 4L, 5L), new CapabilityPolicyAuditQueryRequest(
                1, 20, "approval", "FILE", "filesystem.write", "irreversible",
                "POLICY", 9L, "RUN", "TRACE", LocalDateTime.now().minusDays(1), LocalDateTime.now()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "e.event_type = 'policy.decided'",
                "ar.user_id = ?",
                "ar.tenant_id = ?",
                "ar.organization_id = ?",
                "ar.workspace_id = ?",
                "ar.project_id = ?",
                "jsonb_array_elements_text",
                "e.content::jsonb ->> 'decision' = ?",
                "e.created_at >= ?",
                "e.created_at <= ?"
        );
    }

    @Test
    void shouldCorrelateOnlySafeLifecycleEventTypesWithinSameRunAndUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CapabilityPolicyAuditRepository repository = new CapabilityPolicyAuditRepository(jdbcTemplate);

        repository.findCorrelatedEvents(
                new CapabilityPolicyAuditScope(7L, 2L, 3L, 4L, 5L), "RUN_1", "TOOL_CALL_1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "ar.user_id = ?", "ar.project_id = ?", "e.run_code = ?", "e.content::jsonb ->> 'toolCallId' = ?",
                "'policy.decided'", "'approval.requested'", "'approval.resolved'",
                "'tool.call.requested'", "'tool.call.completed'", "'tool.call.failed'"
        );
    }
}
