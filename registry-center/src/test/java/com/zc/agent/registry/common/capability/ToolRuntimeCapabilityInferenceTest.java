package com.zc.agent.registry.common.capability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRuntimeCapabilityInferenceTest {

    @Test
    void shouldInferFilesystemBoundariesForMcpOperations() {
        assertThat(ToolRuntimeCapabilityInference.infer("mcp", "read_file"))
                .isEqualTo(new ToolRuntimeCapabilityInference.Declaration(
                        java.util.List.of("filesystem.read"), "none"));
        assertThat(ToolRuntimeCapabilityInference.infer("mcp", "write_file"))
                .isEqualTo(new ToolRuntimeCapabilityInference.Declaration(
                        java.util.List.of("filesystem.write"), "irreversible"));
    }

    @Test
    void shouldConservativelyClassifyUnknownMcpMutation() {
        assertThat(ToolRuntimeCapabilityInference.infer("mcp", "send_message"))
                .isEqualTo(new ToolRuntimeCapabilityInference.Declaration(
                        java.util.List.of("external.side_effect"), "irreversible"));
    }
}
