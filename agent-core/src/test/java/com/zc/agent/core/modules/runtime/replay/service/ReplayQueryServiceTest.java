package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.replay.dto.ReplayPageQueryRequest;
import com.zc.agent.core.modules.runtime.replay.model.RuntimeReplayRecord;
import com.zc.agent.core.modules.runtime.replay.repository.RuntimeReplayRepository;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayQueryServiceTest {

    private RuntimeReplayRepository repository;
    private ReplayQueryService service;
    private SecurityUser administrator;

    @BeforeEach
    void setUp() {
        repository = mock(RuntimeReplayRepository.class);
        service = new ReplayQueryService(repository, new ObjectMapper());
        administrator = new SecurityUser(1L, "admin", "Admin", List.of("ADMIN"), List.of("tracing"));
    }

    @Test
    void shouldPageReplayAuditInstancesWithNormalizedFilters() {
        RuntimeReplayRecord record = record();
        when(repository.count("simulation_replay", "succeeded", "RUN_1")).thenReturn(1L);
        when(repository.page("simulation_replay", "succeeded", "RUN_1", 20, 20))
                .thenReturn(List.of(record));

        var page = service.page(
                new ReplayPageQueryRequest(2, 20, " simulation_replay ", "succeeded", " RUN_1 "),
                administrator);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).singleElement().satisfies(item -> {
            assertThat(item.replayCode()).isEqualTo("REPLAY_1");
            assertThat(item.durationMs()).isEqualTo(3000L);
        });
        verify(repository).page("simulation_replay", "succeeded", "RUN_1", 20, 20);
    }

    @Test
    void shouldReturnParsedImmutableSnapshots() {
        when(repository.findByReplayCode("REPLAY_1")).thenReturn(Optional.of(record()));

        var detail = service.detail(" REPLAY_1 ", administrator);

        assertThat(detail.manifest()).containsEntry("manifestVersion", 1);
        assertThat(detail.inputSnapshot()).containsEntry("sourceRunCode", "RUN_1");
        assertThat(detail.resultSnapshot()).containsKey("eventDiff");
    }

    @Test
    void shouldRejectInvalidFilterAndNonAdministrator() {
        assertThatThrownBy(() -> service.page(
                new ReplayPageQueryRequest(1, 20, "unknown", null, null), administrator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Replay 模式无效");

        SecurityUser user = new SecurityUser(2L, "user", "User", List.of("USER"), List.of("tracing"));
        assertThatThrownBy(() -> service.detail("REPLAY_1", user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台管理员");
    }

    @Test
    void shouldFailWhenAuditSnapshotIsDamaged() {
        RuntimeReplayRecord damaged = new RuntimeReplayRecord(
                1L, "REPLAY_1", "REPLAY_TRACE_1", "simulation_replay", "RUN_1",
                20L, null, 1L, "succeeded", "{", "{}", "{}", null,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByReplayCode("REPLAY_1")).thenReturn(Optional.of(damaged));

        assertThatThrownBy(() -> service.detail("REPLAY_1", administrator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审计快照解析失败");
    }

    private RuntimeReplayRecord record() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 31, 10, 0, 0);
        return new RuntimeReplayRecord(
                1L, "REPLAY_1", "REPLAY_TRACE_1", "simulation_replay", "RUN_1",
                20L, null, 1L, "succeeded", "{\"manifestVersion\":1}",
                "{\"sourceRunCode\":\"RUN_1\"}", "{\"eventDiff\":{\"consistent\":true}}", null,
                startedAt, startedAt.plusSeconds(3), startedAt.minusSeconds(1));
    }
}
