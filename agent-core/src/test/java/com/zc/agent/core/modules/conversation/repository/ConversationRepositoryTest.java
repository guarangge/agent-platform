package com.zc.agent.core.modules.conversation.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationRepositoryTest {

    @Test
    void allocatedMessageInsertShouldAtomicallyIncrementConversationSequence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new ConversationRepository.InsertedMessage(7L, 3));
        ConversationRepository repository = new ConversationRepository(jdbc);

        ConversationRepository.InsertedMessage inserted = repository.insertMessageWithAllocatedSequence(
                1L, "user", "content", "success", null, 2L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "UPDATE conversations",
                "SET message_sequence = message_sequence + 1",
                "RETURNING message_sequence AS sequence_no",
                "RETURNING id, sequence_no"
        ).doesNotContain("MAX(message.sequence_no)");
        assertThat(inserted.sequenceNo()).isEqualTo(3);
    }

    @Test
    void taskOperationsQueryShouldAlwaysFilterConversationOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.countTaskOperations(9L, "failed", 7L, null, 1L, "TASK_1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "conversation.user_id = ?",
                "conversation.is_deleted = FALSE",
                "task.is_deleted = FALSE",
                "task.status = ?",
                "task.agent_id = ?",
                "task.id = ?"
        );
    }

    @Test
    void approvalWorkbenchQueryShouldReadAgentNameFromConversation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.findApprovalWorkbenchRecords("pending", null, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("c.agent_name")
                .doesNotContain("t.agent_name");
    }

    @Test
    void updateConversationTitleShouldAlwaysFilterConversationOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.updateConversationTitle(3L, 9L, "新的会话名称", 9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "SET title = ?",
                "WHERE id = ?",
                "AND user_id = ?",
                "AND is_deleted = FALSE"
        );
    }

    @Test
    void assistantMessageQueryShouldBindResultToTaskUserMessage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.findAssistantMessageByTaskId(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "user_message.id = task.message_id",
                "assistant.conversation_id = task.conversation_id",
                "assistant.sequence_no > user_message.sequence_no",
                "assistant.response_status = 'success'",
                "task.id = ?"
        );
    }

    @Test
    void recentContextMessagesShouldExcludeFailedResponses() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.findRecentMessagesByConversationIdBeforeSequenceNo(3L, 8, 6);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "sequence_no < ?",
                "response_status = 'success'",
                "is_deleted = FALSE"
        );
    }

    @Test
    void streamingPreferenceUpdateShouldBeAtomicAndOwnerScoped() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.updateConversationStreamingPreference(3L, 7L, true, 7L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains(
                "jsonb_set",
                "{interactionPolicy}",
                "COALESCE(metadata, '{}'::jsonb)",
                "COALESCE(metadata -> 'interactionPolicy', '{}'::jsonb)",
                "jsonb_build_object('responseStreamingEnabled'",
                "AND user_id = ?",
                "AND is_deleted = FALSE"
        );
    }

    @Test
    void compactionCountShouldUseJsonbFunctionInsteadOfQuestionMarkOperator() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.countCompactionTraceAnalytics(
                9L, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("jsonb_exists(tt.metadata, 'contextCompaction')")
                .doesNotContain("tt.metadata ? 'contextCompaction'");
    }

    @Test
    void compactionPageShouldUseJsonbFunctionInsteadOfQuestionMarkOperator() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConversationRepository repository = new ConversationRepository(jdbc);

        repository.pageCompactionTraceAnalytics(
                9L, null, null, null, null, null, null, null, null, null, 0, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("jsonb_exists(tt.metadata, 'contextCompaction')")
                .doesNotContain("tt.metadata ? 'contextCompaction'");
    }
}
