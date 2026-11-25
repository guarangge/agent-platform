package com.zc.agent.core.modules.cost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.ResourcePrice;
import com.zc.agent.core.modules.cost.repository.CostGovernanceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CostGovernanceServiceEstimateTest {

    private final CostGovernanceRepository repository = mock(CostGovernanceRepository.class);
    private final CostGovernanceService service = new CostGovernanceService(repository, new ObjectMapper());

    @Test
    void shouldEstimateCurrentModelPriceWithoutWritingUsage() {
        when(repository.findEffectivePrice("llm", "MODEL_A")).thenReturn(Optional.of(new ResourcePrice(
                1L, "llm", "MODEL_A", new BigDecimal("0.20"), new BigDecimal("0.80"),
                BigDecimal.ZERO, "CNY", "enabled", LocalDateTime.now(), null)));

        BigDecimal amount = service.estimateAmount(
                "llm", "MODEL_A", 1000, 500, BigDecimal.ONE);

        assertThat(amount).isEqualByComparingTo("0.600000");
    }

    @Test
    void shouldReturnZeroWhenResourceHasNoPrice() {
        when(repository.findEffectivePrice("llm", "UNPRICED")).thenReturn(Optional.empty());

        assertThat(service.estimateAmount("llm", "UNPRICED", 1000, 1000, BigDecimal.ONE))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
