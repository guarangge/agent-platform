package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolContractServiceTest {

    @Test
    void shouldRejectPublishingContractWithoutRegressionCases() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), anyLong())).thenReturn(Map.of(
                "id", 1L, "tool_id", 2L, "input_schema", "{\"type\":\"object\"}"
        ));
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of());
        ToolContractService service = new ToolContractService(jdbc, new ObjectMapper());

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要一个回归用例");
    }
}
