package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 跨步骤结构化绑定、递归 Schema 校验及契约版本兼容服务。 */
@Service
public class StepDataContractService {
    private static final Set<String> SUPPORTED_SCHEMA_KEYWORDS = Set.of(
            "$schema", "$id", "title", "description", "default", "examples", "type", "properties", "required",
            "additionalProperties", "enum", "const", "oneOf", "anyOf", "allOf", "items", "uniqueItems",
            "minItems", "maxItems", "minProperties", "maxProperties", "minLength", "maxLength", "pattern",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum");
    private final ObjectMapper objectMapper;
    private final StepOutputContractService stepOutputContractService;

    public StepDataContractService(ObjectMapper objectMapper, StepOutputContractService stepOutputContractService) {
        this.objectMapper = objectMapper;
        this.stepOutputContractService = stepOutputContractService;
    }

    /** 解析绑定并返回带嵌套结构化输入的新步骤定义。 */
    public ExecutionPlan.ExecutionPlanStep resolveInputs(ExecutionPlan.ExecutionPlanStep step,
                                                          List<RuntimeStepResultContext> previousResults) {
        Map<String, Object> originalInputs = step.inputs() == null ? Map.of() : step.inputs();
        JsonNode contract = objectMapper.valueToTree(originalInputs.get("dataContract"));
        if (!contract.isObject()) return step;
        ObjectNode resolvedRoot = objectMapper.valueToTree(originalInputs);
        JsonNode bindings = contract.path("bindings");
        if (bindings.isObject()) {
            bindings.fields().forEachRemaining(entry -> {
                JsonNode binding = entry.getValue();
                String sourceStepId = binding.path("sourceStepId").asText("");
                String sourcePointer = binding.path("sourcePointer").asText("/structuredData");
                Object value = resolveBinding(previousResults, sourceStepId, sourcePointer);
                if (value == null && binding.path("required").asBoolean(true)) {
                    throw new BusinessException("步骤数据绑定缺失: " + entry.getKey() + " <- " + sourceStepId + sourcePointer);
                }
                if (value != null) {
                    String targetPointer = binding.path("targetPointer").asText(
                            entry.getKey().startsWith("/") ? entry.getKey() : "/" + entry.getKey());
                    setAtPointer(resolvedRoot, targetPointer, objectMapper.valueToTree(value));
                }
            });
        }
        Map<String, Object> resolvedInputs = objectMapper.convertValue(resolvedRoot, Map.class);
        validateSchema(resolvedInputs, contract.path("inputSchema"), "步骤输入");
        return copy(step, resolvedInputs);
    }

    /** 在步骤成功前校验统一输出快照中的 structuredData。 */
    public void validateOutput(ExecutionPlan.ExecutionPlanStep step, String outputSnapshot) {
        JsonNode contract = contract(step);
        if (!contract.isObject() || !contract.path("outputSchema").isObject()) return;
        StandardStepOutputSnapshot snapshot = stepOutputContractService.parseSnapshot(outputSnapshot);
        if (snapshot == null) throw new BusinessException("步骤输出不符合统一输出契约，无法执行结构化校验");
        validateSchema(snapshot.structuredData(), contract.path("outputSchema"), "步骤输出");
    }

    /** Planner 产出后校验契约版本、绑定来源和上下游类型兼容性。 */
    public void validatePlanContracts(List<ExecutionPlan.ExecutionPlanStep> steps) {
        Map<String, ExecutionPlan.ExecutionPlanStep> previous = new LinkedHashMap<>();
        for (ExecutionPlan.ExecutionPlanStep step : steps) {
            JsonNode targetContract = contract(step);
            if (targetContract.isObject()) {
                int version = targetContract.path("version").asInt(1);
                if (version < 1) throw new BusinessException("步骤 " + step.stepId() + " dataContract.version 必须大于0");
                validateExecutableOutputSchema(step, targetContract.path("outputSchema"));
                JsonNode bindings = targetContract.path("bindings");
                if (bindings.isObject()) {
                    bindings.fields().forEachRemaining(entry -> validateBindingDefinition(
                            step, targetContract, entry.getKey(), entry.getValue(), previous));
                }
            }
            previous.put(step.stepId(), step);
        }
    }

    /** Workflow 外层步骤只能声明平台归一化后确实存在的标准输出字段。 */
    private void validateExecutableOutputSchema(ExecutionPlan.ExecutionPlanStep step, JsonNode outputSchema) {
        if (!"workflow".equals(step.stepType()) || !outputSchema.isObject() || !outputSchema.path("required").isArray()) {
            return;
        }
        Set<String> standardFields = Set.of("workflowCode", "workflowName", "summary", "responseText");
        for (JsonNode requiredField : outputSchema.path("required")) {
            String fieldName = requiredField.asText("");
            if (!standardFields.contains(fieldName)) {
                throw new BusinessException("步骤 " + step.stepId() + " 的 Workflow outputSchema 要求不可生成字段: "
                        + fieldName + "，可用标准字段: " + standardFields);
            }
        }
    }

    private void validateBindingDefinition(ExecutionPlan.ExecutionPlanStep target,
                                           JsonNode targetContract,
                                           String bindingName,
                                           JsonNode binding,
                                           Map<String, ExecutionPlan.ExecutionPlanStep> previous) {
        String sourceStepId = binding.path("sourceStepId").asText("");
        ExecutionPlan.ExecutionPlanStep source = previous.get(sourceStepId);
        if (source == null) {
            throw new BusinessException("步骤 " + target.stepId() + " 的绑定 " + bindingName + " 引用了非前序步骤 " + sourceStepId);
        }
        JsonNode sourceContract = contract(source);
        int actualVersion = sourceContract.path("version").asInt(1);
        int expectedVersion = binding.path("sourceContractVersion").asInt(actualVersion);
        if (actualVersion != expectedVersion && !containsInt(sourceContract.path("compatibleVersions"), expectedVersion)) {
            throw new BusinessException("步骤数据契约版本不兼容: " + sourceStepId + " 实际版本 " + actualVersion + "，期望 " + expectedVersion);
        }
        String sourcePointer = binding.path("sourcePointer").asText("/structuredData");
        String targetPointer = binding.path("targetPointer").asText(bindingName.startsWith("/") ? bindingName : "/" + bindingName);
        String sourceType = schemaTypeAtPointer(sourceContract.path("outputSchema"), stripStructuredData(sourcePointer));
        String targetType = schemaTypeAtPointer(targetContract.path("inputSchema"), targetPointer);
        if (!sourceType.isBlank() && !targetType.isBlank() && !typesCompatible(sourceType, targetType)) {
            throw new BusinessException("步骤数据绑定类型不兼容: " + sourceStepId + sourcePointer + "[" + sourceType
                    + "] -> " + target.stepId() + targetPointer + "[" + targetType + "]");
        }
    }

    private Object resolveBinding(List<RuntimeStepResultContext> results,
                                  String sourceStepId,
                                  String pointer) {
        return results.stream().filter(item -> sourceStepId.equals(item.stepId())).findFirst().map(item -> {
            try {
                JsonNode value = objectMapper.readTree(item.outputSnapshot()).at(pointer);
                return value.isMissingNode() || value.isNull() ? null : objectMapper.convertValue(value, Object.class);
            } catch (Exception exception) {
                throw new BusinessException("步骤数据绑定快照损坏: " + sourceStepId);
            }
        }).orElse(null);
    }

    private void validateSchema(Object value, JsonNode schema, String label) {
        if (schema.isObject()) validateNode(objectMapper.valueToTree(value), schema, label);
    }

    private void validateNode(JsonNode node, JsonNode schema, String path) {
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!SUPPORTED_SCHEMA_KEYWORDS.contains(keyword)) {
                throw new BusinessException(path + " 使用了未支持的 Schema 关键字: " + keyword);
            }
        });
        validateAlternatives(node, schema.path("oneOf"), path, true);
        validateAlternatives(node, schema.path("anyOf"), path, false);
        if (schema.path("allOf").isArray()) schema.path("allOf").forEach(item -> validateNode(node, item, path));
        if (schema.has("const") && !node.equals(schema.get("const"))) throw new BusinessException(path + " 不等于契约 const 值");
        if (schema.path("enum").isArray() && !containsNode(schema.path("enum"), node)) throw new BusinessException(path + " 不在契约 enum 范围内");
        validateType(node, schema.path("type").asText(""), path);
        if (node.isObject()) validateObject(node, schema, path);
        if (node.isArray()) validateArray(node, schema, path);
        if (node.isTextual()) validateString(node, schema, path);
        if (node.isNumber()) validateNumber(node, schema, path);
    }

    private void validateObject(JsonNode node, JsonNode schema, String path) {
        if (schema.path("required").isArray()) schema.path("required").forEach(field -> {
            if (!node.hasNonNull(field.asText())) throw new BusinessException(path + " 缺少必填字段: " + field.asText());
        });
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) properties.fields().forEachRemaining(entry -> {
            if (node.hasNonNull(entry.getKey())) validateNode(node.get(entry.getKey()), entry.getValue(), path + "." + entry.getKey());
        });
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true) && properties.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                if (!properties.has(field)) throw new BusinessException(path + " 包含未声明字段: " + field);
            });
        }
        checkSize(node.size(), schema, "minProperties", "maxProperties", path + " 字段数量");
    }

    private void validateArray(JsonNode node, JsonNode schema, String path) {
        checkSize(node.size(), schema, "minItems", "maxItems", path + " 元素数量");
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> values = new HashSet<>();
            node.forEach(item -> { if (!values.add(item)) throw new BusinessException(path + " 存在重复元素"); });
        }
        if (schema.path("items").isObject()) {
            for (int i = 0; i < node.size(); i++) validateNode(node.get(i), schema.path("items"), path + "[" + i + "]");
        }
    }

    private void validateString(JsonNode node, JsonNode schema, String path) {
        checkSize(node.textValue().length(), schema, "minLength", "maxLength", path + " 长度");
        if (schema.hasNonNull("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(node.textValue()).find()) {
            throw new BusinessException(path + " 不匹配 pattern");
        }
    }

    private void validateNumber(JsonNode node, JsonNode schema, String path) {
        double value = node.asDouble();
        if (schema.has("minimum") && value < schema.get("minimum").asDouble()) throw new BusinessException(path + " 小于 minimum");
        if (schema.has("maximum") && value > schema.get("maximum").asDouble()) throw new BusinessException(path + " 大于 maximum");
        if (schema.has("exclusiveMinimum") && value <= schema.get("exclusiveMinimum").asDouble()) throw new BusinessException(path + " 不满足 exclusiveMinimum");
        if (schema.has("exclusiveMaximum") && value >= schema.get("exclusiveMaximum").asDouble()) throw new BusinessException(path + " 不满足 exclusiveMaximum");
    }

    private void validateAlternatives(JsonNode node, JsonNode alternatives, String path, boolean exactlyOne) {
        if (!alternatives.isArray()) return;
        int matches = 0;
        for (JsonNode alternative : alternatives) {
            try { validateNode(node, alternative, path); matches++; } catch (BusinessException ignored) { }
        }
        if ((exactlyOne && matches != 1) || (!exactlyOne && matches == 0)) {
            throw new BusinessException(path + (exactlyOne ? " 不满足 oneOf" : " 不满足 anyOf"));
        }
    }

    private void validateType(JsonNode value, String expectedType, String path) {
        if (expectedType == null || expectedType.isBlank()) return;
        boolean valid = switch (expectedType) {
            case "object" -> value.isObject(); case "array" -> value.isArray(); case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber(); case "number" -> value.isNumber(); case "boolean" -> value.isBoolean();
            case "null" -> value.isNull(); default -> throw new BusinessException("不支持的数据契约类型: " + expectedType);
        };
        if (!valid) throw new BusinessException(path + " 类型不匹配，期望 " + expectedType);
    }

    private void setAtPointer(ObjectNode root, String pointer, JsonNode value) {
        if (pointer == null || !pointer.startsWith("/") || "/".equals(pointer)) throw new BusinessException("dataContract targetPointer 必须是对象字段 JSON Pointer");
        String[] parts = pointer.substring(1).split("/");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = decodePointer(parts[i]); JsonNode child = current.get(part);
            if (child == null || child.isNull()) child = current.putObject(part);
            if (!child.isObject()) throw new BusinessException("dataContract targetPointer 中间节点不是对象: " + part);
            current = (ObjectNode) child;
        }
        current.set(decodePointer(parts[parts.length - 1]), value);
    }

    private JsonNode contract(ExecutionPlan.ExecutionPlanStep step) {
        return objectMapper.valueToTree(step.inputs() == null ? null : step.inputs().get("dataContract"));
    }

    private String schemaTypeAtPointer(JsonNode schema, String pointer) {
        if (!schema.isObject()) return "";
        JsonNode current = schema;
        if (pointer != null && !pointer.isBlank() && !"/".equals(pointer)) {
            if (!pointer.startsWith("/")) return "";
            for (String raw : pointer.substring(1).split("/")) current = current.path("properties").path(decodePointer(raw));
        }
        return current.path("type").asText("");
    }

    private String stripStructuredData(String pointer) {
        if (pointer == null || "/structuredData".equals(pointer)) return "/";
        return pointer.startsWith("/structuredData/") ? pointer.substring("/structuredData".length()) : pointer;
    }

    private boolean typesCompatible(String source, String target) { return source.equals(target) || ("integer".equals(source) && "number".equals(target)); }
    private boolean containsInt(JsonNode array, int value) { if (!array.isArray()) return false; for (JsonNode node : array) if (node.asInt() == value) return true; return false; }
    private boolean containsNode(JsonNode array, JsonNode value) { for (JsonNode item : array) if (item.equals(value)) return true; return false; }
    private void checkSize(int size, JsonNode schema, String min, String max, String label) {
        if (schema.has(min) && size < schema.get(min).asInt()) throw new BusinessException(label + " 小于 " + min);
        if (schema.has(max) && size > schema.get(max).asInt()) throw new BusinessException(label + " 大于 " + max);
    }
    private String decodePointer(String value) { return value.replace("~1", "/").replace("~0", "~"); }

    private ExecutionPlan.ExecutionPlanStep copy(ExecutionPlan.ExecutionPlanStep step, Map<String, Object> inputs) {
        return new ExecutionPlan.ExecutionPlanStep(step.stepNo(), step.stepId(), step.stepType(), step.stepName(),
                step.objective(), step.dependsOnStepIds(), step.targetToolCode(), step.targetWorkflowId(), inputs,
                step.expectedOutput(), step.failurePolicy(), step.replannable(), step.status(), step.description());
    }
}
