package com.zc.agent.core.modules.eval.service;

import com.zc.agent.core.modules.eval.model.EvalRunRecord;
import com.zc.agent.core.modules.eval.repository.EvalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvalReleaseGateServiceTest {
    private final EvalRepository repository = mock(EvalRepository.class);
    private final EvalReleaseGateService service = new EvalReleaseGateService(repository);

    @Test
    void shouldPassFreshHighQualityEvalRun() {
        when(repository.findLatestCompletedRunByAgent(9L)).thenReturn(Optional.of(run(
                "success", 20, 19, 1, "92.5", LocalDateTime.now().minusHours(1))));

        var result = service.evaluate(9L, new BigDecimal("80"), new BigDecimal("90"), 10, 24);

        assertThat(result.passed()).isTrue();
        assertThat(result.passRate()).isEqualByComparingTo("95.00");
    }

    @Test
    void shouldExplainEveryFailedGateCondition() {
        when(repository.findLatestCompletedRunByAgent(9L)).thenReturn(Optional.of(run(
                "failed", 2, 1, 1, "50", LocalDateTime.now().minusHours(48))));

        var result = service.evaluate(9L, new BigDecimal("80"), new BigDecimal("90"), 10, 24);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).hasSize(5);
    }

    private EvalRunRecord run(String status,
                              int total,
                              int passed,
                              int failed,
                              String score,
                              LocalDateTime finishedAt) {
        return new EvalRunRecord(1L, 2L, 1, "SUITE", "Suite", 9L, "Agent", "EVAL_1", status,
                total, passed, failed, new BigDecimal(score), "manual", "{}",
                finishedAt.minusMinutes(5), finishedAt, finishedAt.minusMinutes(5));
    }
}
