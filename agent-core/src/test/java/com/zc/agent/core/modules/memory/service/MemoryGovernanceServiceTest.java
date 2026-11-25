package com.zc.agent.core.modules.memory.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryGovernanceServiceTest {
    @Test
    void shouldOnlyRevokeMemoryOwnedByCurrentUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(3L))).thenReturn(1);
        MemoryGovernanceService service = new MemoryGovernanceService(jdbc);

        assertThat(service.revoke(9L, 3L, "信息已变化")).isTrue();
        verify(jdbc).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("信息已变化"),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(3L));
    }
}
