package com.zc.agent.registry.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminInitializerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldKeepLegacyAdminLockedWhenPasswordIsMissing() {
        pendingAdmin();
        new BootstrapAdminInitializer(jdbc, encoder, "").run(null);
        verify(jdbc, never()).update(anyString(), anyString(), any(Long.class));
    }

    @Test
    void shouldStoreBcryptHashInsteadOfInitialPassword() {
        pendingAdmin();
        String password = "unit-test-bootstrap-password";
        new BootstrapAdminInitializer(jdbc, encoder, password).run(null);

        ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("metadata - 'bootstrapPasswordRequired'"), storedHash.capture(), eq(1L));
        assertThat(storedHash.getValue()).isNotEqualTo(password);
        assertThat(encoder.matches(password, storedHash.getValue())).isTrue();
    }

    @Test
    void shouldNotResetAnAlreadyInitializedAdmin() {
        new BootstrapAdminInitializer(jdbc, encoder, "different-bootstrap-password").run(null);
        verify(jdbc, never()).update(anyString(), anyString(), any(Long.class));
    }

    @Test
    void shouldRejectWeakInitialPassword() {
        pendingAdmin();
        assertThatThrownBy(() -> new BootstrapAdminInitializer(jdbc, encoder, "short").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("至少 12 个字符");
        verify(jdbc, never()).update(anyString(), anyString(), any(Long.class));
    }

    @Test
    void shouldRejectPasswordExceedingBcryptUtf8Limit() {
        pendingAdmin();
        assertThatThrownBy(() -> new BootstrapAdminInitializer(jdbc, encoder, "密".repeat(25)).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("72 字节");
        verify(jdbc, never()).update(anyString(), anyString(), any(Long.class));
    }

    private void pendingAdmin() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of(1L));
    }
}
