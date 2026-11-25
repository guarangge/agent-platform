package com.zc.agent.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    @Test
    void shouldAuthenticateAsyncRedispatchForStreamingResponses() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenService.class));

        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }
}
