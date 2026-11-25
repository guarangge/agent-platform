package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepDataContractServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StepDataContractService service = new StepDataContractService(
            mapper, new StepOutputContractService(mapper));

    @Test
    void shouldRejectWorkflowContractWithInventedRequiredFieldBeforeExecution() {
        ExecutionPlan.ExecutionPlanStep workflowStep = new ExecutionPlan.ExecutionPlanStep(
                1, "workflow_1", "workflow", "通用流程", "执行流程", List.of(), null, 48L,
                Map.of("dataContract", Map.of(
                        "version", 1,
                        "outputSchema", Map.of(
                                "type", "object",
                                "required", List.of("intent"),
                                "properties", Map.of("intent", Map.of("type", "string"))))),
                "返回结果", "fail_fast", true, "draft", "");

        assertThatThrownBy(() -> service.validatePlanContracts(List.of(workflowStep)))
                .hasMessageContaining("Workflow outputSchema 要求不可生成字段: intent");
    }

    @Test
    void shouldBindStructuredOutputIntoNextStepInput() {
        ExecutionPlan.ExecutionPlanStep step = step(Map.of("dataContract", Map.of(
                "bindings", Map.of("school", Map.of(
                        "sourceStepId", "search", "sourcePointer", "/structuredData/name", "required", true)),
                "inputSchema", Map.of("type", "object", "required", List.of("school"),
                        "properties", Map.of("school", Map.of("type", "string")))
        )));
        String snapshot = "{\"resultType\":\"tool\",\"structuredData\":{\"name\":\"南京大学\"}}";
        var result = service.resolveInputs(step, List.of(new RuntimeStepResultContext(
                1, "search", "tool", "检索", "ok", "ok", snapshot)));

        assertThat(result.inputs().get("school")).isEqualTo("南京大学");
    }

    @Test
    void shouldRejectMissingRequiredBinding() {
        ExecutionPlan.ExecutionPlanStep step = step(Map.of("dataContract", Map.of(
                "bindings", Map.of("school", Map.of(
                        "sourceStepId", "missing", "sourcePointer", "/structuredData/name", "required", true))
        )));

        assertThatThrownBy(() -> service.resolveInputs(step, List.of())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据绑定缺失");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBindValueToNestedTargetPointer() {
        ExecutionPlan.ExecutionPlanStep step = step(Map.of("dataContract", Map.of(
                "version", 2,
                "bindings", Map.of("school", Map.of("sourceStepId", "search",
                        "sourcePointer", "/structuredData/name", "targetPointer", "/request/school")),
                "inputSchema", Map.of("type", "object", "properties", Map.of(
                        "request", Map.of("type", "object", "required", List.of("school"),
                                "properties", Map.of("school", Map.of("type", "string", "minLength", 2)))))
        )));
        String snapshot = "{\"resultType\":\"tool\",\"structuredData\":{\"name\":\"南京大学\"}}";

        var result = service.resolveInputs(step, List.of(new RuntimeStepResultContext(
                1, "search", "tool", "检索", "ok", "ok", snapshot)));

        assertThat(((Map<String, Object>) result.inputs().get("request")).get("school")).isEqualTo("南京大学");
    }

    @Test
    void shouldValidateRecursiveOutputSchemaAndEnum() {
        ExecutionPlan.ExecutionPlanStep outputStep = step(Map.of("dataContract", Map.of(
                "outputSchema", Map.of("type", "object", "required", List.of("items"), "properties", Map.of(
                        "items", Map.of("type", "array", "minItems", 1, "uniqueItems", true, "items", Map.of(
                                "type", "object", "required", List.of("status"), "properties", Map.of(
                                        "status", Map.of("type", "string", "enum", List.of("ok", "failed")))))))
        )));
        String invalid = "{\"resultType\":\"tool\",\"structuredData\":{\"items\":[{\"status\":\"unknown\"}]}}";

        assertThatThrownBy(() -> service.validateOutput(outputStep, invalid))
                .isInstanceOf(BusinessException.class).hasMessageContaining("enum");
    }

    @Test
    void shouldRejectIncompatibleContractVersion() {
        ExecutionPlan.ExecutionPlanStep source = new ExecutionPlan.ExecutionPlanStep(1, "search", "tool", "检索", "检索",
                List.of(), "SEARCH", null, Map.of("dataContract", Map.of("version", 2,
                "outputSchema", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))))),
                "结果", "fail_fast", false, "draft", "");
        ExecutionPlan.ExecutionPlanStep target = step(Map.of("dataContract", Map.of("version", 1,
                "bindings", Map.of("school", Map.of("sourceStepId", "search", "sourceContractVersion", 1,
                        "sourcePointer", "/structuredData/name")),
                "inputSchema", Map.of("type", "object", "properties", Map.of("school", Map.of("type", "string"))))));

        assertThatThrownBy(() -> service.validatePlanContracts(List.of(source, target)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("版本不兼容");
    }

    private ExecutionPlan.ExecutionPlanStep step(Map<String, Object> inputs) {
        return new ExecutionPlan.ExecutionPlanStep(2, "reply", "reply", "回复", "回复", List.of("search"),
                null, null, inputs, "结果", "fail_fast", false, "draft", "");
    }
}
