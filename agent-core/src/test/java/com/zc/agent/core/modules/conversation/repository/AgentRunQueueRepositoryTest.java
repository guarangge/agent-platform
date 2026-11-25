package com.zc.agent.core.modules.conversation.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunQueueRepositoryTest {

    @Test
    void shouldProtectCompletionWithWorkerLeaseOwnership() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        AgentRunQueueRepository repository = new AgentRunQueueRepository(jdbcTemplate);

        boolean updated = repository.markCompleted("RUN_1", "worker-a", 1L, "completed");

        assertThat(updated).isFalse();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("worker_id = ?", "status = 'running'");
    }

    @Test
    void claimSqlShouldContainMultiInstanceAndFairnessGuards() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AgentRunQueueRepository repository = new AgentRunQueueRepository(jdbcTemplate);

        repository.claimBatch("worker-a", 60, 4, 16, 30);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "FOR UPDATE OF ar SKIP LOCKED",
                "PARTITION BY user_id, agent_id",
                "effective_priority"
        );
    }
}
