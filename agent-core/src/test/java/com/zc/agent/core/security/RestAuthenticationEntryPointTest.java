package com.zc.agent.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RestAuthenticationEntryPointTest {

    @Test
    void shouldReturnStructuredUnauthorizedResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest(), response, mock(AuthenticationException.class));

        JsonNode payload = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(payload.path("code").asInt()).isEqualTo(401);
        assertThat(payload.path("message").asText()).contains("重新登录");
    }
}
