package com.zc.agent.registry.modules.tool.service;

import com.zc.agent.registry.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryEnvironmentPlaceholderResolverTest {

    @Test
    void shouldResolveSecretWithoutPersistingItsValue() {
        RegistryEnvironmentPlaceholderResolver resolver = new RegistryEnvironmentPlaceholderResolver(
                Map.of("GITHUB_MCP_TOKEN", "secret-value")::get);

        assertThat(resolver.resolve("Bearer ${GITHUB_MCP_TOKEN}")).isEqualTo("Bearer secret-value");
    }

    @Test
    void shouldRejectMissingEnvironmentVariable() {
        RegistryEnvironmentPlaceholderResolver resolver = new RegistryEnvironmentPlaceholderResolver(name -> null);

        assertThatThrownBy(() -> resolver.resolve("${MCP_DATABASE_URL}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP_DATABASE_URL");
    }
}
