package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurableWaitServiceTest {
    @Test
    void shouldRequeueEveryDueTimer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("WAIT_1", "WAIT_2"));
        DurableWaitService service = new DurableWaitService(jdbc, new ObjectMapper());

        service.signalDueTimers();

        verify(jdbc, times(6)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void shouldVerifySignedWebhookAndRejectReplay() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        DurableWaitService service = new DurableWaitService(jdbc, new ObjectMapper());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String eventKey = "WAIT_WEBHOOK";
        String payload = "{\"status\":\"ok\"}";
        String signature = hmac(timestamp + "." + nonce + "." + eventKey + "." + payload);

        service.signalWebhook(eventKey, timestamp, nonce, signature, payload);

        verify(jdbc, times(5)).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void shouldRejectInvalidWebhookSignature() {
        DurableWaitService service = new DurableWaitService(mock(JdbcTemplate.class), new ObjectMapper());
        assertThatThrownBy(() -> service.signalWebhook("WAIT_1",
                String.valueOf(Instant.now().getEpochSecond()), "nonce", "invalid", "{}"))
                .hasMessageContaining("签名校验失败");
    }

    private static String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("agent-wait-webhook-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
