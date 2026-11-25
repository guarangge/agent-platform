package com.zc.agent.core.modules.conversation.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 多实例领取验证。
 * 仅在提供 AGENT_RUN_QUEUE_TEST_DB_URL 时启用，测试库必须已执行 agent-core Flyway migration。
 */
@EnabledIfEnvironmentVariable(named = "AGENT_RUN_QUEUE_TEST_DB_URL", matches = ".+")
class AgentRunQueuePostgresIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("AGENT_RUN_QUEUE_TEST_DB_URL"),
                System.getenv().getOrDefault("AGENT_RUN_QUEUE_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("AGENT_RUN_QUEUE_TEST_DB_PASSWORD", "")
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbcTemplate.update("DELETE FROM agent_runs");
    }

    @Test
    void concurrentWorkersShouldRespectGlobalCapacityAndFairGroups() throws Exception {
        AgentRunQueueRepository firstRepository = new AgentRunQueueRepository(jdbcTemplate);
        AgentRunQueueRepository secondRepository = new AgentRunQueueRepository(jdbcTemplate);
        insert(firstRepository, "RUN_A1", 1L, 101L);
        insert(firstRepository, "RUN_A2", 1L, 101L);
        insert(firstRepository, "RUN_B1", 2L, 202L);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<com.zc.agent.core.modules.conversation.model.AgentRunRecord>> first = executor.submit(() -> {
                start.await();
                return transactionTemplate.execute(status -> firstRepository.claimBatch("worker-a", 60, 2, 2, 30));
            });
            Future<List<com.zc.agent.core.modules.conversation.model.AgentRunRecord>> second = executor.submit(() -> {
                start.await();
                return transactionTemplate.execute(status -> secondRepository.claimBatch("worker-b", 60, 2, 2, 30));
            });
            start.countDown();

            List<com.zc.agent.core.modules.conversation.model.AgentRunRecord> claimed =
                    java.util.stream.Stream.concat(first.get().stream(), second.get().stream()).toList();

            assertThat(claimed).hasSize(2);
            assertThat(claimed).extracting(com.zc.agent.core.modules.conversation.model.AgentRunRecord::userId)
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM agent_runs WHERE status = 'running'", Long.class)).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insert(AgentRunQueueRepository repository, String runCode, Long userId, Long agentId) {
        assertThat(repository.insert(
                runCode, userId * 10, userId, agentId, "input", "{}", runCode,
                3, 50, 100, 100
        )).isPresent();
    }
}
