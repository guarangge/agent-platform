package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentPlaceholderResolverTest {

    @Test
    void shouldResolveMultipleEnvironmentVariables() {
        Map<String, String> values = Map.of("HOST", "db.internal", "PORT", "5432");
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(values::get);

        assertThat(resolver.resolve("postgresql://${HOST}:${PORT}/agent"))
                .isEqualTo("postgresql://db.internal:5432/agent");
    }

    @Test
    void shouldFailWhenRequiredEnvironmentVariableIsMissing() {
        EnvironmentPlaceholderResolver resolver = new EnvironmentPlaceholderResolver(name -> null);

        assertThatThrownBy(() -> resolver.resolve("${MISSING_TOKEN}"))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo("TOOL_ENVIRONMENT_VARIABLE_MISSING");
    }
}
