package com.zc.agent.registry.common.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeCapabilityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNormalizeFrozenRuntimeCapabilities() {
        assertThat(RuntimeCapabilityContract.normalizeCapabilities(List.of(
                " filesystem.read ", "artifact.publish")))
                .containsExactly("filesystem.read", "artifact.publish");

        assertThatThrownBy(() -> RuntimeCapabilityContract.normalizeCapabilities(List.of("host.root")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 Runtime Capability");
        assertThatThrownBy(() -> RuntimeCapabilityContract.normalizeCapabilities(List.of(
                "network.read", "network.read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void shouldRequireTopLevelAndMetadataSideEffectLevelsToMatch() {
        ObjectNode metadata = objectMapper.createObjectNode();
        ObjectNode policy = metadata.putObject("sideEffectPolicy");
        policy.put("level", "irreversible");
        policy.put("idempotencyMode", "required_custom");

        assertThatThrownBy(() -> RuntimeCapabilityContract.validateSideEffect("none", metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须与");
        assertThat(RuntimeCapabilityContract.validateSideEffect("irreversible", metadata))
                .isEqualTo("irreversible");
    }
}
