package com.zc.agent.registry.security;

import com.zc.agent.registry.config.RegistryRuntimeSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryServiceAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateValidRuntimeServiceToken() throws Exception {
        RegistryRuntimeSecurityProperties properties = new RegistryRuntimeSecurityProperties();
        properties.setServiceToken("runtime-token");
        RegistryServiceAuthenticationFilter filter = new RegistryServiceAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RegistryServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "runtime-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly(RegistryServiceAuthenticationFilter.SERVICE_RUNTIME_AUTHORITY);
    }

    @Test
    void shouldIgnoreInvalidRuntimeServiceToken() throws Exception {
        RegistryRuntimeSecurityProperties properties = new RegistryRuntimeSecurityProperties();
        properties.setServiceToken("runtime-token");
        RegistryServiceAuthenticationFilter filter = new RegistryServiceAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RegistryServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "wrong-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
