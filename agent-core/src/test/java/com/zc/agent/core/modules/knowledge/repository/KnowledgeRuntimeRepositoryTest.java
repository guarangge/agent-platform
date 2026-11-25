package com.zc.agent.core.modules.knowledge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Knowledge 混合检索 SQL 参数绑定测试。 */
class KnowledgeRuntimeRepositoryTest {

    @Test
    void shouldBindSearchParametersInSqlPlaceholderOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        KnowledgeRuntimeRepository repository = new KnowledgeRuntimeRepository(jdbcTemplate, new ObjectMapper());
        String vector = "[0.1,0.2]";

        repository.search(List.of(12L, 18L), null, "节能要求", vector, 5, 0.25D);

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), parameters.capture());
        assertThat(parameters.getValue()).containsExactly(
                "节能要求", vector, "节能要求", vector, 12L, 18L, vector, 0.25D, 5);
    }
}
