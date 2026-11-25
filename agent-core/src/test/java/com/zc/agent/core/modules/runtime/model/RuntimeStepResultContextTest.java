package com.zc.agent.core.modules.runtime.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeStepResultContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPreserveLegacyCheckpointJsonContract() throws Exception {
        String legacyJson = """
                {
                  "stepNo": 1,
                  "stepId": "tool_1",
                  "stepType": "tool",
                  "stepName": "查询数据",
                  "summary": "查询成功",
                  "responseText": "已找到结果",
                  "outputSnapshot": "{\\\"resultType\\\":\\\"tool\\\"}"
                }
                """;

        RuntimeStepResultContext result = objectMapper.readValue(legacyJson, RuntimeStepResultContext.class);
        String serialized = objectMapper.writeValueAsString(result);

        assertThat(result.stepNo()).isEqualTo(1);
        assertThat(result.stepId()).isEqualTo("tool_1");
        assertThat(result.outputSnapshot()).isEqualTo("{\"resultType\":\"tool\"}");
        assertThat(objectMapper.readTree(serialized)).isEqualTo(objectMapper.readTree(legacyJson));
    }
}
