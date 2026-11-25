package com.zc.agent.registry.common.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeExecutionModeContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptLegacyAndFrozenDualModeContract() {
        assertThatCode(() -> RuntimeExecutionModeContract.validate(objectMapper.createObjectNode()))
                .doesNotThrowAnyException();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.putObject("executionPolicy")
                .put("defaultMode", "interactive_loop")
                .putArray("allowedModes")
                .add("workflow_plan")
                .add("interactive_loop");

        assertThatCode(() -> RuntimeExecutionModeContract.validate(metadata)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnknownModeAndMissingSafeFallback() {
        ObjectNode unknown = objectMapper.createObjectNode();
        unknown.putObject("executionPolicy")
                .put("defaultMode", "autonomous")
                .putArray("allowedModes")
                .add("workflow_plan");
        assertThatThrownBy(() -> RuntimeExecutionModeContract.validate(unknown))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认执行模式不合法");

        ObjectNode unsafe = objectMapper.createObjectNode();
        unsafe.putObject("executionPolicy")
                .put("defaultMode", "interactive_loop")
                .putArray("allowedModes")
                .add("interactive_loop");
        assertThatThrownBy(() -> RuntimeExecutionModeContract.validate(unsafe))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全回退路径");
    }

    @Test
    void shouldValidateInteractiveLoopBudgets() {
        ObjectNode metadata = objectMapper.createObjectNode();
        ObjectNode execution = metadata.putObject("executionPolicy");
        execution.put("defaultMode", "workflow_plan");
        execution.putArray("allowedModes").add("workflow_plan").add("interactive_loop");
        execution.put("maxLlmCallsPerRun", 6);
        execution.putObject("interactiveLoop")
                .put("maxIterations", 12)
                .put("maxLlmCalls", 6)
                .put("maxToolCalls", 16)
                .put("maxTokens", 50000)
                .put("maxCostAmount", 5.00)
                .put("duplicateToolCallLimit", 2);
        assertThatCode(() -> RuntimeExecutionModeContract.validate(metadata)).doesNotThrowAnyException();

        execution.withObject("interactiveLoop").put("maxLlmCalls", 7);
        assertThatThrownBy(() -> RuntimeExecutionModeContract.validate(metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过单次运行 LLM 上限");

        execution.withObject("interactiveLoop").put("maxLlmCalls", 6).put("duplicateToolCallLimit", 0);
        assertThatThrownBy(() -> RuntimeExecutionModeContract.validate(metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicateToolCallLimit");
    }
}
