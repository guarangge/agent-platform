package com.zc.agent.registry.modules.policy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.policy.dto.CreateGuardrailPolicyRequest;
import com.zc.agent.registry.modules.policy.repository.GuardrailPolicyRepository;
import com.zc.agent.registry.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GuardrailPolicyServiceCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GuardrailPolicyRepository repository = mock(GuardrailPolicyRepository.class);
    private final GuardrailPolicyService service = new GuardrailPolicyService(repository, objectMapper);
    private final SecurityUser operator = new SecurityUser(
            1L, "admin", "管理员", List.of("ROLE_ADMIN"), List.of());

    @Test
    void shouldRejectAllowActionForTextGuardrail() {
        assertThatThrownBy(() -> service.createPolicy(request("allow", objectMapper.createObjectNode()), operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("allow/sandbox 仅允许用于 Capability Policy");

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectUnknownCapabilityAndIncompleteParameterCondition() {
        ObjectNode unknownCapability = capabilityMetadata();
        unknownCapability.with("capabilityPolicy").putArray("capabilities").add("filesystem.root");
        assertThatThrownBy(() -> service.createPolicy(request("block", unknownCapability), operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 Runtime Capability");

        ObjectNode missingValue = capabilityMetadata();
        missingValue.with("capabilityPolicy").putArray("parameterConditions").addObject()
                .put("path", "/operation")
                .put("operator", "equals");
        assertThatThrownBy(() -> service.createPolicy(request("approval", missingValue), operator))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须配置 value 或 values");

        verifyNoInteractions(repository);
    }

    private ObjectNode capabilityMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode().put("policyType", "capability");
        metadata.putObject("capabilityPolicy");
        return metadata;
    }

    private CreateGuardrailPolicyRequest request(String actionType, ObjectNode metadata) {
        return new CreateGuardrailPolicyRequest(
                "capability.test",
                "测试能力策略",
                1,
                100,
                "enabled",
                null,
                "all",
                100,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("prod"),
                "always",
                false,
                null,
                List.of(),
                List.of(),
                actionType,
                null,
                List.of(),
                List.of(),
                "测试策略",
                metadata,
                null
        );
    }
}
