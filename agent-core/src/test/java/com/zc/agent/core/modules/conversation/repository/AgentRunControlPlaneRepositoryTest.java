package com.zc.agent.core.modules.conversation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunControlPlaneRepositoryTest {

    @Test
    void shouldUseLatestHeartbeatStatusWhenDuplicateScopedNodesExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AgentRunControlPlaneRepository repository = new AgentRunControlPlaneRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("worker-1")))
                .thenReturn(List.of(true));

        assertThat(repository.workerAccepting("worker-1")).isTrue();
        verify(jdbcTemplate).query(org.mockito.ArgumentMatchers.contains("ORDER BY last_heartbeat_at DESC,id DESC LIMIT 1"),
                any(RowMapper.class),eq("worker-1"));
    }

    @Test
    void shouldAcceptUnregisteredWorker() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AgentRunControlPlaneRepository repository = new AgentRunControlPlaneRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("worker-new")))
                .thenReturn(List.of());

        assertThat(repository.workerAccepting("worker-new")).isTrue();
    }
}
