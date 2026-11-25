package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceDataSanitizerTest {

    private final TraceDataSanitizer sanitizer = new TraceDataSanitizer(new ObjectMapper());

    @Test
    void shouldRedactSensitiveFieldsRecursively() {
        JsonNode result = sanitizer.sanitize(Map.of(
                "authorization", "Bearer abc.def.ghi",
                "nested", List.of(Map.of("api_key", "secret-key", "content", "safe"))
        ));

        assertThat(result.path("authorization").asText()).isEqualTo(TraceDataSanitizer.REDACTED);
        assertThat(result.path("nested").path(0).path("api_key").asText()).isEqualTo(TraceDataSanitizer.REDACTED);
        assertThat(result.path("nested").path(0).path("content").asText()).isEqualTo("safe");
    }

    @Test
    void shouldRedactCredentialsEmbeddedInText() {
        String result = sanitizer.sanitizeError(
                "request failed Authorization: Bearer abc.def.ghi password=123456 apiKey: sk-test"
        );

        assertThat(result).doesNotContain("abc.def.ghi", "123456", "sk-test");
        assertThat(result).contains("Bearer [REDACTED]", "password=[REDACTED]", "apiKey: [REDACTED]");
    }

    @Test
    void shouldLimitLongTraceText() {
        JsonNode result = sanitizer.sanitize(Map.of("content", "x".repeat(9_000)));

        assertThat(result.path("content").asText())
                .hasSize(8_014)
                .endsWith("...[TRUNCATED]");
    }
}
