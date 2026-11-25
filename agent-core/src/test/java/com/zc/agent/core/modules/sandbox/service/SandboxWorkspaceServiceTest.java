package com.zc.agent.core.modules.sandbox.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.config.SandboxProperties;
import com.zc.agent.core.modules.sandbox.dto.SandboxInputFileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCollectAndCleanupIsolatedWorkspace() throws Exception {
        SandboxWorkspaceService service = service(1024);
        SandboxWorkspaceService.Workspace workspace = service.create("TEST", List.of(input("input/a.txt", "hello")), 1024);
        Files.writeString(workspace.path().resolve("result.txt"), "done", StandardCharsets.UTF_8);

        var artifacts = service.collectArtifacts(workspace.path(), List.of("result.txt"), 1024);

        assertThat(workspace.inputBytes()).isEqualTo(5);
        assertThat(new String(Base64.getDecoder().decode(artifacts.get(0).contentBase64()), StandardCharsets.UTF_8)).isEqualTo("done");
        service.cleanup(workspace.path());
        assertThat(workspace.path()).doesNotExist();
    }

    @Test
    void shouldRejectInvalidBase64AndInputOverflow() throws Exception {
        SandboxWorkspaceService service = service(4);
        assertThatThrownBy(() -> service.create("BAD", List.of(new SandboxInputFileRequest("a.txt", "not-base64")), 4))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Base64");
        assertThatThrownBy(() -> service.create("BIG", List.of(input("a.txt", "hello")), 4))
                .isInstanceOf(BusinessException.class).hasMessageContaining("超过");
    }

    private SandboxWorkspaceService service(long maxBytes) throws Exception {
        SandboxProperties properties = new SandboxProperties();
        properties.setWorkspaceRoot(tempDir);
        properties.setMaxInputBytes(maxBytes);
        properties.setMaxArtifactBytes(maxBytes);
        SandboxWorkspaceService service = new SandboxWorkspaceService(properties, new SandboxPolicyValidator());
        service.initialize();
        return service;
    }

    private SandboxInputFileRequest input(String path, String content) {
        return new SandboxInputFileRequest(path, Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
    }
}
