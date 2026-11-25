package com.zc.agent.core.modules.sandbox.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPolicyValidatorTest {

    private final SandboxPolicyValidator validator = new SandboxPolicyValidator();

    @Test
    void shouldAcceptPinnedImageAndWhitelistedCommand() {
        assertThatCode(() -> validator.validateExecution(profile(), List.of("python3", "--version"), null, List.of("result.json")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectLatestImageAndCommandOutsideWhitelist() {
        SandboxProfileRecord latest = profile("runtime:latest", List.of("python3"));
        assertThatThrownBy(() -> validator.validateProfile(latest)).isInstanceOf(BusinessException.class).hasMessageContaining("latest");
        assertThatThrownBy(() -> validator.validateExecution(profile(), List.of("sh", "-c", "id"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("白名单");
        assertThatThrownBy(() -> validator.validateProfile(profile("registry.example:5000/runtime", List.of("python3"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("明确版本");
    }

    @Test
    void shouldRejectWorkspaceTraversalAndAbsolutePath() {
        assertThatThrownBy(() -> validator.validateExecution(profile(), List.of("python3"), null, List.of("../secret")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("越界");
        assertThatThrownBy(() -> validator.validateExecution(profile(), List.of("python3"), null, List.of("/etc/passwd")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("相对路径");
    }

    private SandboxProfileRecord profile() { return profile("agent-sandbox-runtime:1.0.0", List.of("python3")); }

    private SandboxProfileRecord profile(String image, List<String> commands) {
        return new SandboxProfileRecord(1L, "TEST", "测试", image, commands, "none", 134217728L,
                500000000L, 64L, 10, 4096L, 1048576L, "enabled", LocalDateTime.now(), LocalDateTime.now());
    }
}
