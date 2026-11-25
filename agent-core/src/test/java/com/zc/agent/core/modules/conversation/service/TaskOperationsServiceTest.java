package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.modules.conversation.dto.TaskOperationsPageQueryRequest;
import com.zc.agent.core.modules.conversation.model.TaskOperationsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOperationsServiceTest {

    @Test
    void shouldPageOnlyCurrentUsersTasksWithNormalizedFilters() {
        ConversationRepository repository = mock(ConversationRepository.class);
        TaskOperationsService service = new TaskOperationsService(repository);
        SecurityUser user = new SecurityUser(9L, "tester", "测试用户", List.of("USER"), List.of("chat"));
        TaskOperationsPageQueryRequest request = new TaskOperationsPageQueryRequest(2, 10, " success ", 7L, null, 1L, " TASK ");
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(3);
        LocalDateTime finishedAt = startedAt.plusSeconds(2);
        TaskOperationsRecord record = new TaskOperationsRecord(
                1L, "TASK_1", "RUN_1", 2L, "CONV_1", "会话", 7L, "Agent",
                null, 1L, "primary", "workflow", "TRACE_1", 1, "success", null,
                3, 3, 0, 1, 0, 0, startedAt, finishedAt, startedAt
        );
        when(repository.countTaskOperations(9L, "success", 7L, null, 1L, "TASK")).thenReturn(11L);
        when(repository.pageTaskOperations(9L, "success", 7L, null, 1L, "TASK", 10, 10)).thenReturn(List.of(record));

        var result = service.page(request, user);

        assertThat(result.total()).isEqualTo(11L);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).durationMs()).isEqualTo(2000L);
        verify(repository).pageTaskOperations(9L, "success", 7L, null, 1L, "TASK", 10, 10);
    }
}
