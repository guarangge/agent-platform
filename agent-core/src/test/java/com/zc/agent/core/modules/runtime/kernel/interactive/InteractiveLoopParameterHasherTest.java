package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveLoopParameterHasherTest {

    private final InteractiveLoopParameterHasher hasher = new InteractiveLoopParameterHasher(new ObjectMapper());

    @Test
    void shouldIgnoreObjectKeyOrderButPreserveArrayOrder() {
        LinkedHashMap<String, Object> first = new LinkedHashMap<>();
        first.put("b", Map.of("y", 2, "x", 1));
        first.put("a", List.of("one", "two"));
        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("a", List.of("one", "two"));
        reordered.put("b", Map.of("x", 1, "y", 2));

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(reordered));
        assertThat(hasher.hash(Map.of("a", List.of("two", "one"))))
                .isNotEqualTo(hasher.hash(Map.of("a", List.of("one", "two"))));
    }
}
