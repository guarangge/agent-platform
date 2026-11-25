package com.zc.agent.core.modules.runtime.protocol.controller;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventStreamService;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeEventControllerTest {

    @Test
    void shouldUseLargerCursorAndSetStreamingHeaders() {
        RuntimeEventStreamService streamService = mock(RuntimeEventStreamService.class);
        Authentication authentication = mock(Authentication.class);
        SecurityUser securityUser = mock(SecurityUser.class);
        StreamingResponseBody expected = outputStream -> { };
        when(authentication.getPrincipal()).thenReturn(securityUser);
        when(streamService.stream("RUN_1", 12L, securityUser)).thenReturn(expected);
        RuntimeEventController controller = new RuntimeEventController(streamService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        StreamingResponseBody actual = controller.streamEvents(
                "RUN_1", 8L, "12", authentication, response);

        assertThat(actual).isSameAs(expected);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-transform");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        verify(streamService).stream("RUN_1", 12L, securityUser);
    }

    @Test
    void shouldRejectInvalidCursors() {
        RuntimeEventController controller = new RuntimeEventController(mock(RuntimeEventStreamService.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mock(SecurityUser.class));

        assertThatThrownBy(() -> controller.streamEvents(
                "RUN_1", -1L, null, authentication, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("afterSequence 必须是非负整数");
        assertThatThrownBy(() -> controller.streamEvents(
                "RUN_1", 0L, "invalid", authentication, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Last-Event-ID 必须是非负整数");
    }
}
