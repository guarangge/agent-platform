package com.zc.agent.core.modules.runtime.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskPlanControlServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TaskPlanControlService service = new TaskPlanControlService(jdbc);

    @Test
    void shouldConsumeSkipAtTargetStep() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(List.of(Map.of(
                "id", 1L, "action", "skip_step", "target_step_no", 2)));
        assertThat(service.beforeStep(9L, 2).skip()).isTrue();
    }

    @Test
    void shouldPauseAfterSingleStep() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(List.of(Map.of(
                "id", 1L, "action", "single_step", "target_step_no", 2)));
        assertThatThrownBy(() -> service.beforeStep(9L, 3)).isInstanceOf(PlanControlPauseException.class);
    }

    @Test
    void shouldNotRunIntermediateStepsBeforeSingleStepTarget() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(List.of(Map.of(
                "id", 1L, "action", "single_step", "target_step_no", 2)));
        assertThatThrownBy(() -> service.beforeStep(9L, 1))
                .isInstanceOf(PlanControlPauseException.class)
                .hasMessageContaining("目标步骤 2");
    }
}
